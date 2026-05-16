package studio.one.platform.autoconfigure.skillgraph;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.service.SkillMatchPolicy;
import studio.one.platform.skillgraph.application.service.DefaultSkillCandidateReviewService;
import studio.one.platform.skillgraph.application.service.DefaultSkillDictionaryService;
import studio.one.platform.skillgraph.application.service.DefaultSkillExtractionService;
import studio.one.platform.skillgraph.application.service.DefaultSkillGraphService;
import studio.one.platform.skillgraph.application.service.DefaultSkillMappingService;
import studio.one.platform.skillgraph.application.service.DefaultSkillRecommendationService;
import studio.one.platform.skillgraph.application.service.DefaultSkillTaxonomyService;
import studio.one.platform.skillgraph.application.service.DefaultSkillVisualizationService;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphService;
import studio.one.platform.skillgraph.application.usecase.SkillMappingService;
import studio.one.platform.skillgraph.application.usecase.SkillRecommendationService;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.skillgraph.application.usecase.SkillVisualizationService;
import studio.one.platform.skillgraph.domain.port.SkillClusterer;
import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.SkillGraphStore;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;
import studio.one.platform.skillgraph.infrastructure.extraction.PatternSkillCandidateExtractor;
import studio.one.platform.skillgraph.infrastructure.clustering.DistanceThresholdSkillClusterer;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillGraphStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillMappingStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillProjectionStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillTaxonomyStore;

@AutoConfiguration
@EnableConfigurationProperties({ SkillGraphFeatureProperties.class, SkillGraphProperties.class })
@ConditionalOnProperty(prefix = "studio.features.skillgraph", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SkillGraphAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SkillCandidateExtractor skillCandidateExtractor(SkillGraphProperties properties) {
        return new PatternSkillCandidateExtractor(properties.getExtraction().getMaxTerms());
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillEmbeddingPort noopSkillEmbeddingPort() {
        return new NoOpSkillEmbeddingPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillMatchPolicy skillMatchPolicy(SkillGraphProperties properties) {
        return new SkillMatchPolicy(
                properties.getMatching().getMatchThreshold(),
                properties.getMatching().getAliasThreshold());
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillClusterer skillClusterer(SkillGraphProperties properties) {
        return new DistanceThresholdSkillClusterer(properties.getClustering().getRadius());
    }

    @Bean(name = SkillProjectionStore.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillProjectionStore skillProjectionStore() {
        return new InMemorySkillProjectionStore();
    }

    @Bean(name = SkillTaxonomyStore.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillTaxonomyStore skillTaxonomyStore() {
        return new InMemorySkillTaxonomyStore();
    }

    @Bean(name = SkillGraphStore.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillGraphStore skillGraphStore() {
        return new InMemorySkillGraphStore();
    }

    @Bean(name = SkillMappingStore.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillMappingStore skillMappingStore() {
        return new InMemorySkillMappingStore();
    }

    @Bean(name = SkillExtractionService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillExtractionService skillExtractionService(
            SkillCandidateStore candidateStore,
            SkillDictionaryStore dictionaryStore,
            SkillCandidateExtractor extractor,
            SkillEmbeddingPort embeddingPort,
            SkillMatchPolicy matchPolicy) {
        return new DefaultSkillExtractionService(candidateStore, dictionaryStore, extractor, embeddingPort, matchPolicy);
    }

    @Bean(name = SkillCandidateReviewService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillCandidateReviewService skillCandidateReviewService(
            SkillCandidateStore candidateStore,
            SkillDictionaryStore dictionaryStore) {
        return new DefaultSkillCandidateReviewService(candidateStore, dictionaryStore);
    }

    @Bean(name = SkillDictionaryService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillDictionaryService skillDictionaryService(SkillDictionaryStore dictionaryStore) {
        return new DefaultSkillDictionaryService(dictionaryStore);
    }

    @Bean(name = SkillVisualizationService.SERVICE_NAME)
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "studio.one.platform.ai.core.vector.visualization.UmapVectorProjectionGenerator")
    public SkillVisualizationService skillVisualizationService(
            SkillDictionaryStore dictionaryStore,
            SkillProjectionStore projectionStore,
            SkillClusterer clusterer) {
        return new DefaultSkillVisualizationService(dictionaryStore, projectionStore, clusterer);
    }

    @Bean(name = SkillTaxonomyService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillTaxonomyService skillTaxonomyService(SkillTaxonomyStore taxonomyStore) {
        return new DefaultSkillTaxonomyService(taxonomyStore);
    }

    @Bean(name = SkillGraphService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillGraphService skillGraphService(SkillGraphStore graphStore) {
        return new DefaultSkillGraphService(graphStore);
    }

    @Bean(name = SkillMappingService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillMappingService skillMappingService(SkillMappingStore mappingStore) {
        return new DefaultSkillMappingService(mappingStore);
    }

    @Bean(name = SkillRecommendationService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillRecommendationService skillRecommendationService(SkillMappingStore mappingStore) {
        return new DefaultSkillRecommendationService(mappingStore);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "studio.skillgraph", name = "persistence", havingValue = "memory", matchIfMissing = true)
    static class MemoryPersistenceConfig {

        @Bean(name = SkillCandidateStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillCandidateStore skillCandidateStore() {
            return new InMemorySkillCandidateStore();
        }

        @Bean(name = SkillDictionaryStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillDictionaryStore skillDictionaryStore() {
            return new InMemorySkillDictionaryStore();
        }

    }

    @Configuration
    @ConditionalOnProperty(prefix = "studio.skillgraph", name = "persistence", havingValue = "jdbc")
    @ConditionalOnClass(NamedParameterJdbcTemplate.class)
    @ConditionalOnBean(NamedParameterJdbcTemplate.class)
    @RequiredArgsConstructor
    static class JdbcPersistenceConfig {

        @Bean(name = SkillCandidateStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillCandidateStore skillCandidateStore(NamedParameterJdbcTemplate template) {
            return new JdbcSkillCandidateStore(template);
        }

        @Bean(name = SkillDictionaryStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillDictionaryStore skillDictionaryStore(NamedParameterJdbcTemplate template) {
            return new JdbcSkillDictionaryStore(template);
        }
    }
}
