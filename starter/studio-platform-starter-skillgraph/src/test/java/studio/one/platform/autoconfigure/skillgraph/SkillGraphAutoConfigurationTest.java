package studio.one.platform.autoconfigure.skillgraph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.skillgraph.application.service.DefaultSkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryDraftService;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphService;
import studio.one.platform.skillgraph.application.usecase.SkillMappingService;
import studio.one.platform.skillgraph.application.usecase.SkillRecommendationService;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillGraphStore;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;
import studio.one.platform.skillgraph.domain.port.SkillRagExtractionJobStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class SkillGraphAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SkillGraphAutoConfiguration.class));

    @Test
    void createsDefaultMemorySkillGraphBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SkillCandidateExtractor.class);
            assertThat(context).hasSingleBean(SkillCandidateStore.class);
            assertThat(context).hasSingleBean(SkillDictionaryStore.class);
            assertThat(context).hasSingleBean(SkillExtractionService.class);
            assertThat(context).hasSingleBean(SkillCandidateReviewService.class);
            assertThat(context).hasSingleBean(SkillDictionaryService.class);
            assertThat(context).hasSingleBean(SkillCategoryDraftService.class);
            assertThat(context).hasSingleBean(SkillTaxonomyStore.class);
            assertThat(context).hasSingleBean(SkillGraphStore.class);
            assertThat(context).hasSingleBean(SkillMappingStore.class);
            assertThat(context).hasSingleBean(SkillRagExtractionJobStore.class);
            assertThat(context).hasSingleBean(SkillTaxonomyService.class);
            assertThat(context).hasSingleBean(SkillGraphService.class);
            assertThat(context).hasSingleBean(SkillMappingService.class);
            assertThat(context).hasSingleBean(SkillRecommendationService.class);
            assertThat(context).getBean(SkillExtractionService.class)
                    .isInstanceOf(DefaultSkillExtractionService.class);
        });
    }

    @Test
    void createsRagExtractionJobServiceAfterWebRagResolverIsAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SkillGraphAutoConfiguration.class,
                        SkillGraphWebAutoConfiguration.SkillGraphRagExtractionConfig.class))
                .withBean(RagPipelineService.class, FakeRagPipelineService::new)
                .withPropertyValues(
                        "studio.features.skillgraph.web.enabled=true",
                        "studio.skillgraph.extraction.rag-job.batch-size=10",
                        "studio.skillgraph.extraction.rag-job.worker-count=1",
                        "studio.skillgraph.extraction.rag-job.queue-capacity=2",
                        "studio.skillgraph.extraction.rag-job.max-chunks=100",
                        "studio.skillgraph.extraction.rag-job.max-text-bytes-per-batch=20000")
                .run(context -> assertThat(context).hasSingleBean(SkillRagExtractionJobService.class));
    }

    @Test
    void createsLlmSkillExtractionServiceWhenEnabled() {
        contextRunner
                .withPropertyValues("studio.skillgraph.extraction.mode=llm")
                .withBean(PromptRenderer.class, FakePromptRenderer::new)
                .withBean(ChatPort.class, () -> request -> new ChatResponse(
                        List.of(ChatMessage.assistant("[{\"term\":\"Spring Boot\",\"confidence\":0.9}]")),
                        "test",
                        Map.of()))
                .run(context -> assertThat(context).getBean(SkillExtractionService.class)
                        .isInstanceOf(DefaultSkillExtractionService.class));
    }

    @Test
    void failsLlmSkillExtractionServiceWhenAiBeansAreMissing() {
        contextRunner
                .withPropertyValues("studio.skillgraph.extraction.mode=llm")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void backsOffWhenFeatureIsDisabled() {
        contextRunner
                .withPropertyValues("studio.features.skillgraph.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SkillExtractionService.class));
    }

    private static final class FakePromptRenderer implements PromptRenderer {

        @Override
        public String render(String name, Map<String, Object> params) {
            return "prompt";
        }

        @Override
        public String getRawPrompt(String name) {
            return "prompt";
        }
    }

    private static final class FakeRagPipelineService implements RagPipelineService {

        @Override
        public void index(RagIndexRequest request) {
        }

        @Override
        public List<RagSearchResult> search(RagSearchRequest request) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> searchByObject(RagSearchRequest request, String objectType, String objectId) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
            return List.of(new RagSearchResult("doc-1", "Spring Boot", Map.of("chunkId", "chunk-1"), 1.0d));
        }

        @Override
        public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
            return Optional.empty();
        }
    }
}
