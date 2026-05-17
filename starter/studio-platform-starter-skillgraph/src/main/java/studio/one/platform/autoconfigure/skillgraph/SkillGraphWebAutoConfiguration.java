package studio.one.platform.autoconfigure.skillgraph;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphService;
import studio.one.platform.skillgraph.application.usecase.SkillMappingService;
import studio.one.platform.skillgraph.application.usecase.SkillRecommendationService;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.skillgraph.application.usecase.SkillVisualizationService;
import studio.one.platform.skillgraph.web.controller.SkillCandidateMgmtController;
import studio.one.platform.skillgraph.web.controller.SkillDictionaryMgmtController;
import studio.one.platform.skillgraph.web.controller.SkillExtractionJobMgmtController;
import studio.one.platform.skillgraph.web.controller.SkillGraphMgmtController;
import studio.one.platform.skillgraph.web.controller.SkillGraphSimulationMgmtController;
import studio.one.platform.skillgraph.web.controller.SkillMappingMgmtController;
import studio.one.platform.skillgraph.web.controller.SkillRecommendationMgmtController;
import studio.one.platform.skillgraph.web.controller.SkillTaxonomyMgmtController;
import studio.one.platform.skillgraph.web.controller.SkillVisualizationMgmtController;

@AutoConfiguration(after = SkillGraphAutoConfiguration.class)
@ConditionalOnProperty(prefix = "studio.features.skillgraph.web", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SkillGraphWebAutoConfiguration {

    @Configuration
    @ConditionalOnBean(name = SkillExtractionService.SERVICE_NAME)
    @Import({SkillExtractionJobMgmtController.class, SkillGraphSimulationMgmtController.class})
    static class SkillExtractionWebConfig {
    }

    @Configuration
    @ConditionalOnClass(name = "studio.one.platform.ai.service.pipeline.RagPipelineService")
    static class SkillGraphRagExtractionConfig {

        @Bean
        @ConditionalOnBean(RagPipelineService.class)
        @ConditionalOnMissingBean(SkillGraphRagChunkResolver.class)
        SkillGraphRagChunkResolver skillGraphRagChunkResolver(RagPipelineService ragPipelineService) {
            return new RagPipelineSkillGraphRagChunkResolver(ragPipelineService);
        }
    }

    @Configuration
    @ConditionalOnBean(name = SkillCandidateReviewService.SERVICE_NAME)
    @Import(SkillCandidateMgmtController.class)
    static class SkillCandidateWebConfig {
    }

    @Configuration
    @ConditionalOnBean(name = SkillDictionaryService.SERVICE_NAME)
    @Import(SkillDictionaryMgmtController.class)
    static class SkillDictionaryWebConfig {
    }

    @Configuration
    @ConditionalOnBean(name = SkillVisualizationService.SERVICE_NAME)
    @Import(SkillVisualizationMgmtController.class)
    static class SkillVisualizationWebConfig {
    }

    @Configuration
    @ConditionalOnBean(name = SkillTaxonomyService.SERVICE_NAME)
    @Import(SkillTaxonomyMgmtController.class)
    static class SkillTaxonomyWebConfig {
    }

    @Configuration
    @ConditionalOnBean(name = SkillGraphService.SERVICE_NAME)
    @Import(SkillGraphMgmtController.class)
    static class SkillGraphWebConfig {
    }

    @Configuration
    @ConditionalOnBean(name = SkillMappingService.SERVICE_NAME)
    @Import(SkillMappingMgmtController.class)
    static class SkillMappingWebConfig {
    }

    @Configuration
    @ConditionalOnBean(name = SkillRecommendationService.SERVICE_NAME)
    @Import(SkillRecommendationMgmtController.class)
    static class SkillRecommendationWebConfig {
    }
}
