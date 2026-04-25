package studio.one.platform.chunking.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.service.FixedSizeChunker;
import studio.one.platform.chunking.service.HeadingChunkContextExpander;
import studio.one.platform.chunking.service.ParentChildChunkContextExpander;
import studio.one.platform.chunking.service.RecursiveChunker;
import studio.one.platform.chunking.service.StructureBasedChunker;
import studio.one.platform.chunking.service.TableChunkContextExpander;
import studio.one.platform.chunking.service.WindowChunkContextExpander;

class ChunkingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChunkingAutoConfiguration.class));

    @Test
    void registersRecursiveDefaultsWhenEnabledByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(ChunkingProperties.class)
                .hasSingleBean(FixedSizeChunker.class)
                .hasSingleBean(RecursiveChunker.class)
                .hasSingleBean(StructureBasedChunker.class)
                .hasSingleBean(WindowChunkContextExpander.class)
                .hasSingleBean(ParentChildChunkContextExpander.class)
                .hasSingleBean(HeadingChunkContextExpander.class)
                .hasSingleBean(TableChunkContextExpander.class)
                .hasSingleBean(ChunkingOrchestrator.class));
    }

    @Test
    void doesNotRegisterDefaultBeansWhenDisabled() {
        contextRunner.withPropertyValues("studio.chunking.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(FixedSizeChunker.class)
                        .doesNotHaveBean(RecursiveChunker.class)
                        .doesNotHaveBean(StructureBasedChunker.class)
                        .doesNotHaveBean(WindowChunkContextExpander.class)
                        .doesNotHaveBean(ParentChildChunkContextExpander.class)
                        .doesNotHaveBean(HeadingChunkContextExpander.class)
                        .doesNotHaveBean(TableChunkContextExpander.class)
                        .doesNotHaveBean(ChunkingOrchestrator.class));
    }

    @Test
    void allowsCustomOrchestratorOverride() {
        ChunkingOrchestrator custom = context -> java.util.List.of();

        contextRunner.withBean(ChunkingOrchestrator.class, () -> custom)
                .run(context -> assertThat(context.getBean(ChunkingOrchestrator.class)).isSameAs(custom));
    }

    @Test
    void appliesConfiguredDefaultsWhenContextDoesNotOverride() {
        contextRunner.withPropertyValues(
                        "studio.chunking.strategy=fixed-size",
                        "studio.chunking.max-size=5",
                        "studio.chunking.overlap=1")
                .run(context -> {
                    ChunkingOrchestrator orchestrator = context.getBean(ChunkingOrchestrator.class);
                    var chunks = orchestrator.chunk(ChunkingContext.configuredDefaults("abcdefghij")
                            .sourceDocumentId("doc")
                            .build());

                    assertThat(chunks).extracting(chunk -> chunk.content())
                            .containsExactly("abcde", "efghi", "ij");
                });
    }
}
