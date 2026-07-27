package studio.one.platform.chunking.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.service.BlockifyChunker;
import studio.one.platform.chunking.service.BlockifyGenerator;
import studio.one.platform.chunking.service.FixedSizeChunker;
import studio.one.platform.chunking.service.HeadingChunkContextExpander;
import studio.one.platform.chunking.service.KnowledgeBlockChunker;
import studio.one.platform.chunking.service.LlmBlockifyGenerator;
import studio.one.platform.chunking.service.ParentChildChunkContextExpander;
import studio.one.platform.chunking.service.PiiMaskingBlockifyGenerator;
import studio.one.platform.chunking.service.RecursiveChunker;
import studio.one.platform.chunking.service.StructureBasedChunker;
import studio.one.platform.chunking.service.TableChunkContextExpander;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.chunking.service.WindowChunkContextExpander;

class ChunkingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChunkingAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void registersRecursiveDefaultsWhenEnabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context)
                    .hasSingleBean(ChunkingProperties.class)
                    .hasSingleBean(FixedSizeChunker.class)
                    .hasSingleBean(RecursiveChunker.class)
                    .hasSingleBean(StructureBasedChunker.class)
                    .hasSingleBean(BlockifyGenerator.class)
                    .hasSingleBean(KnowledgeBlockChunker.class)
                    .hasSingleBean(WindowChunkContextExpander.class)
                    .hasSingleBean(ParentChildChunkContextExpander.class)
                    .hasSingleBean(HeadingChunkContextExpander.class)
                    .hasSingleBean(TableChunkContextExpander.class)
                    .hasSingleBean(TextractNormalizedDocumentAdapter.class)
                    .hasSingleBean(ChunkingOrchestrator.class);
            assertThat(context).hasBean("blockifyChunker");
            assertThat(context.getBean("blockifyChunker")).isExactlyInstanceOf(BlockifyChunker.class);
        });
    }

    @Test
    void doesNotRegisterDefaultBeansWhenDisabled() {
        contextRunner.withPropertyValues("studio.chunking.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(FixedSizeChunker.class)
                        .doesNotHaveBean(RecursiveChunker.class)
                        .doesNotHaveBean(StructureBasedChunker.class)
                        .doesNotHaveBean(BlockifyGenerator.class)
                        .doesNotHaveBean(BlockifyChunker.class)
                        .doesNotHaveBean(KnowledgeBlockChunker.class)
                        .doesNotHaveBean(WindowChunkContextExpander.class)
                        .doesNotHaveBean(ParentChildChunkContextExpander.class)
                        .doesNotHaveBean(HeadingChunkContextExpander.class)
                        .doesNotHaveBean(TableChunkContextExpander.class)
                        .doesNotHaveBean(TextractNormalizedDocumentAdapter.class)
                        .doesNotHaveBean(ChunkingOrchestrator.class));
    }

    @Test
    void allowsCustomTextractNormalizedDocumentAdapterOverride() {
        TextractNormalizedDocumentAdapter custom = new TextractNormalizedDocumentAdapter();

        contextRunner.withBean(TextractNormalizedDocumentAdapter.class, () -> custom)
                .run(context -> assertThat(context.getBean(TextractNormalizedDocumentAdapter.class)).isSameAs(custom));
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

    @Test
    void rejectsBlockifyWhenDisabledByDefault() {
        contextRunner.run(context -> {
            ChunkingOrchestrator orchestrator = context.getBean(ChunkingOrchestrator.class);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> orchestrator.chunk(
                            ChunkingContext.builder("본문 내용입니다.")
                                    .sourceDocumentId("doc")
                                    .strategy(ChunkingStrategyType.BLOCKIFY)
                                    .build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Blockify chunking is disabled");
        });
    }

    @Test
    void enablesBlockifyWhenConfigured() {
        contextRunner.withPropertyValues("studio.chunking.blockify.enabled=true")
                .run(context -> {
                    ChunkingOrchestrator orchestrator = context.getBean(ChunkingOrchestrator.class);

                    var chunks = orchestrator.chunk(ChunkingContext.builder(
                                    "수료 기준은 전체 진도율 80% 이상을 충족하고 최종 평가에서 60점 이상을 취득하는 것입니다. "
                                            + "교육 담당자는 두 조건을 모두 확인한 뒤 수료 여부를 확정해야 하며, "
                                            + "어느 하나라도 충족하지 못하면 보완 학습 또는 재평가 대상으로 분류합니다.")
                            .sourceDocumentId("doc")
                            .strategy(ChunkingStrategyType.BLOCKIFY)
                            .build());

                    assertThat(chunks).hasSize(1);
                    assertThat(chunks.get(0).metadata().toMap())
                            .containsEntry("requestedChunkingStrategy", "blockify")
                            .containsEntry("actualChunkingStrategy", "blockify");
                });
    }

    @Test
    void enablesKnowledgeBlockWhenConfigured() {
        contextRunner.withPropertyValues("studio.chunking.knowledge-block.enabled=true")
                .run(context -> {
                    ChunkingOrchestrator orchestrator = context.getBean(ChunkingOrchestrator.class);

                    var chunks = orchestrator.chunk(ChunkingContext.builder(
                                    "수료 기준은 전체 진도율 80% 이상을 충족하고 최종 평가에서 60점 이상을 취득하는 것입니다. "
                                            + "교육 담당자는 두 조건을 모두 확인한 뒤 수료 여부를 확정해야 하며, "
                                            + "어느 하나라도 충족하지 못하면 보완 학습 또는 재평가 대상으로 분류합니다.")
                            .sourceDocumentId("doc")
                            .strategy(ChunkingStrategyType.KNOWLEDGE_BLOCK)
                            .build());

                    assertThat(chunks).hasSize(1);
                    assertThat(chunks.get(0).metadata().toMap())
                            .containsEntry("schemaVersion", "knowledge-block-metadata-v1")
                            .containsEntry("requestedChunkingStrategy", "knowledge-block")
                            .containsEntry("actualChunkingStrategy", "knowledge-block")
                            .containsKey("knowledgeBlockFingerprint");
                });
    }

    @Test
    void registersPiiMaskingLlmBlockifyGeneratorWhenConfigured() {
        ChatPort chatPort = request -> new ChatResponse(
                java.util.List.of(ChatMessage.assistant("""
                        [{
                          "title":"수료 기준",
                          "question":"수료 기준은 무엇인가?",
                          "answer":"수료 기준은 전체 진도율 80% 이상과 최종 평가 60점 이상을 모두 충족하는 것이다.",
                          "keywords":["수료 기준"],
                          "sourceEvidence":[{"text":"수료 기준은 전체 진도율 80% 이상과 최종 평가 60점 이상을 모두 충족하는 것이다."}]
                        }]
                        """)),
                request.model(),
                java.util.Map.of());
        AiProviderRegistry registry = new AiProviderRegistry(
                "google-ai-gemini",
                java.util.Map.of("google-ai-gemini", chatPort),
                java.util.Map.of());

        contextRunner
                .withBean(AiProviderRegistry.class, () -> registry)
                .withPropertyValues(
                        "studio.chunking.blockify.enabled=true",
                        "studio.chunking.blockify.generator-type=llm",
                        "studio.chunking.blockify.llm-provider=google-ai-gemini",
                        "studio.chunking.blockify.llm-model=gemini-2.5-flash")
                .run(context -> {
                    assertThat(context).hasSingleBean(BlockifyGenerator.class);
                    assertThat(context.getBean(BlockifyGenerator.class))
                            .isInstanceOf(PiiMaskingBlockifyGenerator.class);
                });
    }

    @Test
    void registersPlainLlmBlockifyGeneratorWhenPiiMaskingDisabled() {
        ChatPort chatPort = request -> new ChatResponse(
                java.util.List.of(ChatMessage.assistant("[]")),
                request.model(),
                java.util.Map.of());
        AiProviderRegistry registry = new AiProviderRegistry(
                "google-ai-gemini",
                java.util.Map.of("google-ai-gemini", chatPort),
                java.util.Map.of());

        contextRunner
                .withBean(AiProviderRegistry.class, () -> registry)
                .withPropertyValues(
                        "studio.chunking.blockify.enabled=true",
                        "studio.chunking.blockify.generator-type=llm",
                        "studio.chunking.blockify.pii-masking.enabled=false",
                        "studio.chunking.blockify.llm-provider=google-ai-gemini",
                        "studio.chunking.blockify.llm-model=gemini-2.5-flash")
                .run(context -> {
                    assertThat(context).hasSingleBean(BlockifyGenerator.class);
                    assertThat(context.getBean(BlockifyGenerator.class))
                            .isInstanceOf(LlmBlockifyGenerator.class);
                });
    }
}
