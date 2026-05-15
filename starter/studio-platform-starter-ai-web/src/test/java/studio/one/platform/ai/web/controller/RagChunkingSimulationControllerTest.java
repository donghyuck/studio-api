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
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.web.dto.RagChunkingSimulationRequestDto;
import studio.one.platform.ai.web.dto.RagChunkingSimulationResponseDto;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.web.dto.ApiResponse;

class RagChunkingSimulationControllerTest {

    @Test
    void simulatesTokenAwareChunkingWithTokenizerMetadata() {
        CapturingOrchestrator orchestrator = new CapturingOrchestrator(List.of(chunk("doc-0", "alpha beta", 0, 6)));
        RagChunkingSimulationController controller = new RagChunkingSimulationController(
                orchestrator,
                new AiWebRagProperties(),
                environment());

        ResponseEntity<ApiResponse<RagChunkingSimulationResponseDto>> response = controller.simulateChunking(
                new RagChunkingSimulationRequestDto(
                        "alpha beta",
                        "attachment",
                        "42",
                        "att-1",
                        "openai",
                        "text-embedding-3-small",
                        true,
                        "token",
                        500,
                        80,
                        800));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(orchestrator.context.unit()).isEqualTo(ChunkUnit.TOKEN);
        assertThat(orchestrator.context.maxSize()).isEqualTo(500);
        assertThat(orchestrator.context.overlap()).isEqualTo(80);
        assertThat(orchestrator.context.metadata())
                .containsEntry(VectorRecord.KEY_EMBEDDING_PROVIDER, "openai")
                .containsEntry(VectorRecord.KEY_EMBEDDING_MODEL, "text-embedding-3-small")
                .containsEntry("attachmentId", "att-1")
                .containsEntry("tokenizerAutoDetect", true);

        RagChunkingSimulationResponseDto body = response.getBody().getData();
        assertThat(body.totalChunks()).isEqualTo(1);
        assertThat(body.totalTokens()).isEqualTo(6);
        assertThat(body.tokenizer().tokenizerProvider()).isEqualTo("tiktoken");
        assertThat(body.tokenizer().tokenizerEncoding()).isEqualTo("cl100k_base");
        assertThat(body.tokenizer().selectionSource()).isEqualTo("model-mapping");
        assertThat(body.tokenizer().chunkUnit()).isEqualTo("token");
        assertThat(body.chunks().get(0).tokenCount()).isEqualTo(6);
        assertThat(body.chunks().get(0).embeddingModel()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void reportsFallbackAndMaxChunkSizeWarnings() {
        Chunk fallbackChunk = Chunk.of("doc-0", "oversized", ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 0)
                .chunkType(ChunkType.CHILD)
                .tokenCount(12)
                .attributes(Map.of(
                        ChunkMetadata.KEY_CHUNK_TOKEN_COUNT, 12,
                        ChunkMetadata.KEY_TOKENIZER_PROVIDER, "approximate",
                        ChunkMetadata.KEY_TOKENIZER_ENCODING, "approximate",
                        ChunkMetadata.KEY_TOKENIZER_SELECTION_SOURCE, "fallback",
                        ChunkMetadata.KEY_TOKENIZER_CONFIDENCE, "low",
                        ChunkMetadata.KEY_TOKENIZER_FALLBACK_USED, true,
                        ChunkMetadata.KEY_TOKENIZER_WARNINGS, List.of("No tokenizer mapping is available")))
                .build());
        RagChunkingSimulationController controller = new RagChunkingSimulationController(
                new CapturingOrchestrator(List.of(fallbackChunk)),
                new AiWebRagProperties(),
                environment());

        RagChunkingSimulationResponseDto body = controller.simulateChunking(new RagChunkingSimulationRequestDto(
                "oversized",
                null,
                null,
                null,
                "custom",
                "custom-model",
                true,
                "token",
                500,
                80,
                8)).getBody().getData();

        assertThat(body.tokenizer().fallbackUsed()).isTrue();
        assertThat(body.tokenizer().warnings()).contains("No tokenizer mapping is available");
        assertThat(body.warnings()).anyMatch(warning -> warning.contains("exceeds maxChunkSize"));
        assertThat(body.chunks().get(0).warnings()).anyMatch(warning -> warning.contains("maxChunkSize"));
    }

    @Test
    void reportsConfiguredChunkDefaultsWhenRequestOmitsSizeAndOverlap() {
        RagChunkingSimulationController controller = new RagChunkingSimulationController(
                new CapturingOrchestrator(List.of(chunk("doc-0", "alpha", 0, 2))),
                new AiWebRagProperties(),
                environment());

        RagChunkingSimulationResponseDto body = controller.simulateChunking(new RagChunkingSimulationRequestDto(
                "alpha",
                null,
                null,
                null,
                "openai",
                "text-embedding-3-small",
                true,
                "token",
                null,
                null,
                null)).getBody().getData();

        assertThat(body.tokenizer().chunkSize()).isEqualTo(120);
        assertThat(body.tokenizer().chunkOverlap()).isEqualTo(12);
    }

    @Test
    void rejectsInvalidRequestsWithStatusCodes() {
        RagChunkingSimulationController missingOrchestrator = new RagChunkingSimulationController(
                null,
                new AiWebRagProperties(),
                environment());
        assertStatus(() -> missingOrchestrator.simulateChunking(new RagChunkingSimulationRequestDto(
                "alpha", null, null, null, null, null, null, null, null, null, null)), 503);

        RagChunkingSimulationController controller = new RagChunkingSimulationController(
                new CapturingOrchestrator(List.of()),
                new AiWebRagProperties(),
                environment());
        assertStatus(() -> controller.simulateChunking(new RagChunkingSimulationRequestDto(
                " ", null, null, null, null, null, null, null, null, null, null)), 400);
        assertStatus(() -> controller.simulateChunking(new RagChunkingSimulationRequestDto(
                "alpha", null, null, null, null, null, null, "bad-unit", null, null, null)), 400);
    }

    private Chunk chunk(String id, String content, int order, int tokenCount) {
        return Chunk.of(id, content, ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, order)
                .chunkType(ChunkType.CHILD)
                .tokenCount(tokenCount)
                .attributes(Map.of(
                        ChunkMetadata.KEY_CHUNK_TOKEN_COUNT, tokenCount,
                        ChunkMetadata.KEY_TOKENIZER_PROVIDER, "tiktoken",
                        ChunkMetadata.KEY_TOKENIZER_ENCODING, "cl100k_base",
                        ChunkMetadata.KEY_TOKENIZER_SELECTION_SOURCE, "model-mapping",
                        ChunkMetadata.KEY_TOKENIZER_CONFIDENCE, "high",
                        VectorRecord.KEY_EMBEDDING_MODEL, "text-embedding-3-small"))
                .build());
    }

    private StandardEnvironment environment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "studio.chunking.max-size", "120",
                "studio.chunking.overlap", "12")));
        return environment;
    }

    private void assertStatus(Runnable action, int expectedStatus) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(expectedStatus));
    }

    private static final class CapturingOrchestrator implements ChunkingOrchestrator {
        private final List<Chunk> chunks;
        private ChunkingContext context;

        private CapturingOrchestrator(List<Chunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<Chunk> chunk(ChunkingContext context) {
            this.context = context;
            return chunks;
        }
    }
}
