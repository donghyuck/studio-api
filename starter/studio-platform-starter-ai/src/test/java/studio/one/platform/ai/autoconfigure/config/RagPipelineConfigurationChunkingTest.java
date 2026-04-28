package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.chunk.OverlapTextChunker;
import studio.one.platform.ai.service.pipeline.InMemoryRagIndexJobRepository;
import studio.one.platform.ai.service.pipeline.JdbcRagIndexJobRepository;
import studio.one.platform.ai.service.pipeline.RagIndexJobRepository;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.chunking.autoconfigure.ChunkingAutoConfiguration;
import studio.one.platform.chunking.core.ChunkingOrchestrator;

@SuppressWarnings("deprecation")
class RagPipelineConfigurationChunkingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagPipelineConfiguration.class))
            .withBean(EmbeddingPort.class, () -> mock(EmbeddingPort.class))
            .withBean(VectorStorePort.class, () -> mock(VectorStorePort.class));

    @Test
    void createsLegacyTextChunkerFallbackWhenChunkingOrchestratorIsMissing() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(TextChunker.class)
                .hasSingleBean(OverlapTextChunker.class)
                .hasSingleBean(RagPipelineService.class));
    }

    @Test
    void doesNotCreateLegacyTextChunkerWhenChunkingOrchestratorExists() {
        contextRunner.withBean(ChunkingOrchestrator.class, () -> request -> List.of())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TextChunker.class);
                    assertThat(context).doesNotHaveBean(OverlapTextChunker.class);
                    assertThat(context).hasSingleBean(ChunkingOrchestrator.class);
                    assertThat(context).hasSingleBean(RagPipelineService.class);
                });
    }

    @Test
    void doesNotCreateLegacyTextChunkerWhenChunkingAutoConfigurationProvidesOrchestrator() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ChunkingAutoConfiguration.class,
                        RagPipelineConfiguration.class))
                .withBean(EmbeddingPort.class, () -> mock(EmbeddingPort.class))
                .withBean(VectorStorePort.class, () -> mock(VectorStorePort.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ChunkingOrchestrator.class);
                    assertThat(context).doesNotHaveBean(TextChunker.class);
                    assertThat(context).doesNotHaveBean(OverlapTextChunker.class);
                    assertThat(context).hasSingleBean(RagPipelineService.class);
                });
    }

    @Test
    void keepsUserDefinedTextChunkerWhenChunkingOrchestratorIsMissing() {
        TextChunker customChunker = (documentId, text) -> List.of();

        contextRunner.withBean(TextChunker.class, () -> customChunker)
                .run(context -> {
                    assertThat(context).hasSingleBean(TextChunker.class);
                    assertThat(context).doesNotHaveBean(OverlapTextChunker.class);
                    assertThat(context.getBean(TextChunker.class)).isSameAs(customChunker);
                    assertThat(context).hasSingleBean(RagPipelineService.class);
                });
    }

    @Test
    void keepsUserDefinedTextChunkerWithoutCreatingDefaultWhenChunkingOrchestratorAlsoExists() {
        TextChunker customChunker = (documentId, text) -> List.of();

        contextRunner.withBean(TextChunker.class, () -> customChunker)
                .withBean(ChunkingOrchestrator.class, () -> request -> List.of())
                .run(context -> {
                    assertThat(context).hasSingleBean(TextChunker.class);
                    assertThat(context).doesNotHaveBean(OverlapTextChunker.class);
                    assertThat(context.getBean(TextChunker.class)).isSameAs(customChunker);
                    assertThat(context).hasSingleBean(ChunkingOrchestrator.class);
                    assertThat(context).hasSingleBean(RagPipelineService.class);
                });
    }

    @Test
    void createsJdbcRagIndexJobRepositoryWhenOptedIn() {
        contextRunner
                .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
                .withPropertyValues("studio.ai.rag.jobs.repository=jdbc")
                .run(context -> {
                    assertThat(context).hasSingleBean(RagIndexJobRepository.class);
                    assertThat(context).hasSingleBean(JdbcRagIndexJobRepository.class);
                    assertThat(context).doesNotHaveBean(InMemoryRagIndexJobRepository.class);
                });
    }

    @Test
    void createsJdbcRagIndexJobRepositoryFromLegacyPipelineOptIn() {
        contextRunner
                .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
                .withPropertyValues("studio.ai.pipeline.jobs.repository=jdbc")
                .run(context -> {
                    assertThat(context).hasSingleBean(RagIndexJobRepository.class);
                    assertThat(context).hasSingleBean(JdbcRagIndexJobRepository.class);
                    assertThat(context).doesNotHaveBean(InMemoryRagIndexJobRepository.class);
                });
    }
}
