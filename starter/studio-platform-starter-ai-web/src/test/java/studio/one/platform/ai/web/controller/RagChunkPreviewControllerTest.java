package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.autoconfigure.config.RagPipelineProperties;
import studio.one.platform.ai.web.dto.RagChunkConfigResponseDto;
import studio.one.platform.ai.web.dto.RagChunkPreviewRequestDto;
import studio.one.platform.ai.web.dto.RagChunkPreviewResponseDto;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.web.dto.ApiResponse;

class RagChunkPreviewControllerTest {

    @Test
    void previewsChunksWithExplicitOptionsAndMetadata() {
        CapturingChunkingOrchestrator orchestrator = new CapturingChunkingOrchestrator();
        RagChunkPreviewController controller = controller(orchestrator);

        ResponseEntity<ApiResponse<RagChunkPreviewResponseDto>> response = controller.preview(
                new RagChunkPreviewRequestDto(
                        "alpha beta gamma",
                        "doc-1",
                        "attachment",
                        "42",
                        "text/plain",
                        "sample.txt",
                        "recursive",
                        100,
                        10,
                        "token",
                        Map.of("category", "manual")));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(orchestrator.context.sourceDocumentId()).isEqualTo("doc-1");
        assertThat(orchestrator.context.objectType()).isEqualTo("attachment");
        assertThat(orchestrator.context.objectId()).isEqualTo("42");
        assertThat(orchestrator.context.contentType()).isEqualTo("text/plain");
        assertThat(orchestrator.context.filename()).isEqualTo("sample.txt");
        assertThat(orchestrator.context.strategy()).isEqualTo(ChunkingStrategyType.RECURSIVE);
        assertThat(orchestrator.context.maxSize()).isEqualTo(100);
        assertThat(orchestrator.context.overlap()).isEqualTo(10);
        assertThat(orchestrator.context.unit()).isEqualTo(ChunkUnit.TOKEN);
        assertThat(orchestrator.context.metadata()).containsEntry("category", "manual");

        RagChunkPreviewResponseDto body = response.getBody().getData();
        assertThat(body.totalChunks()).isEqualTo(1);
        assertThat(body.strategy()).isEqualTo("recursive");
        assertThat(body.maxSize()).isEqualTo(100);
        assertThat(body.overlap()).isEqualTo(10);
        assertThat(body.unit()).isEqualTo("token");
        assertThat(body.chunks()).hasSize(1);
        assertThat(body.chunks().get(0).chunkId()).isEqualTo("doc-1-0");
        assertThat(body.chunks().get(0).chunkOrder()).isZero();
        assertThat(body.chunks().get(0).chunkType()).isEqualTo("child");
        assertThat(body.chunks().get(0).metadata()).containsEntry("category", "manual");
    }

    @Test
    void usesConfiguredDefaultsWhenOptionsAreMissing() {
        CapturingChunkingOrchestrator orchestrator = new CapturingChunkingOrchestrator();
        RagChunkPreviewController controller = controller(orchestrator);

        ResponseEntity<ApiResponse<RagChunkPreviewResponseDto>> response = controller.preview(
                new RagChunkPreviewRequestDto(
                        "alpha",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(orchestrator.context.strategy()).isEqualTo(ChunkingStrategyType.FIXED_SIZE);
        assertThat(orchestrator.context.maxSize()).isEqualTo(ChunkingContext.USE_CONFIGURED_MAX_SIZE);
        assertThat(orchestrator.context.overlap()).isEqualTo(ChunkingContext.USE_CONFIGURED_OVERLAP);
        assertThat(response.getBody().getData().strategy()).isEqualTo("fixed-size");
        assertThat(response.getBody().getData().maxSize()).isEqualTo(120);
        assertThat(response.getBody().getData().overlap()).isEqualTo(12);
    }

    @Test
    void rejectsInvalidPreviewRequests() {
        RagChunkPreviewController controller = controller(new CapturingChunkingOrchestrator());

        assertStatus(() -> controller.preview(new RagChunkPreviewRequestDto(
                " ", null, null, null, null, null, null, null, null, null, null)), 400);
        assertStatus(() -> controller.preview(new RagChunkPreviewRequestDto(
                "alpha", null, null, null, null, null, "semantic", null, null, null, null)), 400);
        assertStatus(() -> controller.preview(new RagChunkPreviewRequestDto(
                "alpha", null, null, null, null, null, null, 10, 10, null, null)), 400);
    }

    @Test
    void rejectsUnsupportedConfiguredDefaultStrategyWhenStrategyIsOmitted() {
        RagChunkPreviewController controller = controller(
                new CapturingChunkingOrchestrator(),
                new AiWebRagProperties(),
                "semantic");

        assertStatus(() -> controller.preview(new RagChunkPreviewRequestDto(
                "alpha", null, null, null, null, null, null, null, null, null, null)), 400);

        ResponseEntity<ApiResponse<RagChunkConfigResponseDto>> config = controller.config();
        assertThat(config.getBody().getData().chunking().strategy()).isEqualTo("semantic");
        assertThat(config.getBody().getData().chunking().previewStrategy()).isNull();
        assertThat(config.getBody().getData().chunking().defaultStrategyPreviewSupported()).isFalse();
    }

    @Test
    void returnsServiceUnavailableWhenChunkingOrchestratorIsMissing() {
        RagChunkPreviewController controller = controller(null);

        assertStatus(() -> controller.preview(new RagChunkPreviewRequestDto(
                "alpha", null, null, null, null, null, null, null, null, null, null)), 503);
    }

    @Test
    void rejectsInputLongerThanPreviewLimit() {
        AiWebRagProperties properties = new AiWebRagProperties();
        properties.getChunkPreview().setMaxInputChars(3);
        RagChunkPreviewController controller = controller(new CapturingChunkingOrchestrator(), properties);

        assertStatus(() -> controller.preview(new RagChunkPreviewRequestDto(
                "alpha", null, null, null, null, null, null, null, null, null, null)), 400);
    }

    @Test
    void rejectsPreviewWhenPreviewIsDisabledButKeepsConfigAvailable() {
        AiWebRagProperties properties = new AiWebRagProperties();
        properties.getChunkPreview().setEnabled(false);
        RagChunkPreviewController controller = controller(new CapturingChunkingOrchestrator(), properties);

        assertStatus(() -> controller.preview(new RagChunkPreviewRequestDto(
                "alpha", null, null, null, null, null, null, null, null, null, null)), 404);

        ResponseEntity<ApiResponse<RagChunkConfigResponseDto>> response = controller.config();
        assertThat(response.getBody().getData().limits().enabled()).isFalse();
    }

    @Test
    void truncatesReturnedChunksAtPreviewLimit() {
        AiWebRagProperties properties = new AiWebRagProperties();
        properties.getChunkPreview().setMaxPreviewChunks(1);
        RagChunkPreviewController controller = controller(context -> List.of(
                chunk("doc-0", "first", 0),
                chunk("doc-1", "second", 1)), properties);

        ResponseEntity<ApiResponse<RagChunkPreviewResponseDto>> response = controller.preview(
                new RagChunkPreviewRequestDto(
                        "alpha beta", null, null, null, null, null, null, null, null, null, null));

        assertThat(response.getBody().getData().totalChunks()).isEqualTo(2);
        assertThat(response.getBody().getData().chunks()).hasSize(1);
        assertThat(response.getBody().getData().warnings()).containsExactly("Chunk preview truncated to 1 chunks.");
    }

    @Test
    void configExposesSafeChunkingAndRagSettings() {
        RagChunkPreviewController controller = controller(new CapturingChunkingOrchestrator());

        ResponseEntity<ApiResponse<RagChunkConfigResponseDto>> response = controller.config();

        RagChunkConfigResponseDto data = response.getBody().getData();
        assertThat(data.chunking().available()).isTrue();
        assertThat(data.chunking().strategy()).isEqualTo("fixed-size");
        assertThat(data.chunking().previewStrategy()).isEqualTo("fixed-size");
        assertThat(data.chunking().defaultStrategyPreviewSupported()).isTrue();
        assertThat(data.chunking().maxSize()).isEqualTo(120);
        assertThat(data.chunking().overlap()).isEqualTo(12);
        assertThat(data.chunking().registeredChunkers()).containsExactly("TestChunker");
        assertThat(data.legacyFallback().chunkSize()).isEqualTo(500);
        assertThat(data.legacyFallback().chunkOverlap()).isEqualTo(50);
        assertThat(data.ragContext().expansion().maxCandidates()).isEqualTo(100);
        assertThat(data.limits().maxInputChars()).isEqualTo(200_000);
        assertThat(data.limits().maxPreviewChunks()).isEqualTo(500);
    }

    @Test
    void configReportsUnavailableChunkingWhenOrchestratorIsMissing() {
        RagChunkPreviewController controller = controller(null);

        ResponseEntity<ApiResponse<RagChunkConfigResponseDto>> response = controller.config();

        assertThat(response.getBody().getData().chunking().available()).isFalse();
        assertThat(response.getBody().getData().chunking().chunkingOrchestratorAvailable()).isFalse();
    }

    private RagChunkPreviewController controller(ChunkingOrchestrator orchestrator) {
        return controller(orchestrator, new AiWebRagProperties());
    }

    private RagChunkPreviewController controller(ChunkingOrchestrator orchestrator, AiWebRagProperties properties) {
        return controller(orchestrator, properties, "fixed-size");
    }

    private RagChunkPreviewController controller(
            ChunkingOrchestrator orchestrator,
            AiWebRagProperties properties,
            String configuredStrategy) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "studio.chunking.strategy", configuredStrategy,
                "studio.chunking.max-size", "120",
                "studio.chunking.overlap", "12")));
        return new RagChunkPreviewController(
                orchestrator,
                List.of(new TestChunker()),
                null,
                new RagPipelineProperties(),
                properties,
                environment);
    }

    private Chunk chunk(String id, String content, int order) {
        return Chunk.of(id, content, ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, order)
                .chunkType(ChunkType.CHILD)
                .build());
    }

    private void assertStatus(Runnable action, int expectedStatus) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(expectedStatus));
    }

    private static final class CapturingChunkingOrchestrator implements ChunkingOrchestrator {
        private ChunkingContext context;

        @Override
        public List<Chunk> chunk(ChunkingContext context) {
            this.context = context;
            ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 0)
                    .sourceDocumentId(context.sourceDocumentId() == null ? "doc" : context.sourceDocumentId())
                    .chunkType(ChunkType.CHILD)
                    .objectType(context.objectType())
                    .objectId(context.objectId())
                    .attributes(context.metadata())
                    .build();
            return List.of(Chunk.of(
                    (context.sourceDocumentId() == null ? "doc" : context.sourceDocumentId()) + "-0",
                    context.text(),
                    metadata));
        }
    }

    private static final class TestChunker implements Chunker {
        @Override
        public ChunkingStrategyType strategy() {
            return ChunkingStrategyType.RECURSIVE;
        }

        @Override
        public List<Chunk> chunk(ChunkingContext context) {
            return List.of();
        }
    }
}
