package studio.one.platform.autoconfigure.skillgraph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.skillgraph.application.service.LlmSkillCandidateExtractor;
import studio.one.platform.skillgraph.application.service.RegexSkillCandidateExtractor;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphService;
import studio.one.platform.skillgraph.application.usecase.SkillMappingService;
import studio.one.platform.skillgraph.application.usecase.SkillRecommendationService;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillGraphStore;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

import java.util.List;
import java.util.Map;

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
            assertThat(context).hasSingleBean(SkillTaxonomyStore.class);
            assertThat(context).hasSingleBean(SkillGraphStore.class);
            assertThat(context).hasSingleBean(SkillMappingStore.class);
            assertThat(context).hasSingleBean(SkillTaxonomyService.class);
            assertThat(context).hasSingleBean(SkillGraphService.class);
            assertThat(context).hasSingleBean(SkillMappingService.class);
            assertThat(context).hasSingleBean(SkillRecommendationService.class);
            assertThat(context).getBean(SkillExtractionService.class)
                    .isInstanceOf(RegexSkillCandidateExtractor.class);
        });
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
                        .isInstanceOf(LlmSkillCandidateExtractor.class));
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
}
