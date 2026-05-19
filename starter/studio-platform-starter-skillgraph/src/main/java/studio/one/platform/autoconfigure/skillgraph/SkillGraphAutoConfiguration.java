package studio.one.platform.autoconfigure.skillgraph;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.skillgraph.application.service.SkillMatchPolicy;
import studio.one.platform.skillgraph.application.service.DefaultSkillCandidateReviewService;
import studio.one.platform.skillgraph.application.service.DefaultSkillCategoryDraftService;
import studio.one.platform.skillgraph.application.service.DefaultSkillDictionaryService;
import studio.one.platform.skillgraph.application.service.DefaultSkillExtractionService;
import studio.one.platform.skillgraph.application.service.DefaultSkillGraphService;
import studio.one.platform.skillgraph.application.service.DefaultSkillMappingService;
import studio.one.platform.skillgraph.application.service.DefaultSkillRecommendationService;
import studio.one.platform.skillgraph.application.service.DefaultSkillTaxonomyService;
import studio.one.platform.skillgraph.application.service.DefaultSkillVisualizationService;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryDraftService;
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
import studio.one.platform.skillgraph.domain.port.SkillRagExtractionJobStore;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;
import studio.one.platform.skillgraph.infrastructure.extraction.LlmSkillCandidateExtractor;
import studio.one.platform.skillgraph.infrastructure.extraction.PatternSkillCandidateExtractor;
import studio.one.platform.skillgraph.infrastructure.clustering.DistanceThresholdSkillClusterer;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillGraphStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillMappingStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillProjectionStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillRagExtractionJobStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillTaxonomyStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillGraphStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillMappingStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillProjectionStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillRagExtractionJobStore;
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

    @Bean(name = SkillExtractionService.SERVICE_NAME)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "studio.skillgraph.extraction", name = "mode", havingValue = "regex", matchIfMissing = true)
    public SkillExtractionService regexSkillExtractionService(
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
    public SkillDictionaryService skillDictionaryService(
            SkillDictionaryStore dictionaryStore,
            SkillEmbeddingPort embeddingPort,
            @Qualifier("skillRagExtractionJobExecutor") Executor embeddingJobExecutor) {
        return new DefaultSkillDictionaryService(dictionaryStore, embeddingPort, embeddingJobExecutor);
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

    @Bean(name = SkillCategoryDraftService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public SkillCategoryDraftService skillCategoryDraftService(
            SkillProjectionStore projectionStore,
            SkillDictionaryStore dictionaryStore,
            SkillTaxonomyStore taxonomyStore) {
        return new DefaultSkillCategoryDraftService(projectionStore, dictionaryStore, taxonomyStore);
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

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "skillRagExtractionJobExecutor")
    public ThreadPoolExecutor skillRagExtractionJobExecutor(SkillGraphProperties properties) {
        SkillGraphProperties.RagJob ragJob = properties.getExtraction().getRagJob();
        int workers = Math.max(1, ragJob.getWorkerCount());
        return new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, ragJob.getQueueCapacity())),
                runnable -> {
                    Thread thread = new Thread(runnable, "skill-rag-extraction-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Configuration
    @ConditionalOnProperty(prefix = "studio.skillgraph.extraction", name = "mode", havingValue = "llm")
    @ConditionalOnClass(name = {
            "studio.one.platform.ai.core.chat.ChatPort",
            "studio.one.platform.ai.service.prompt.PromptRenderer"
    })
    static class LlmExtractionConfig {

        @Bean(name = SkillExtractionService.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillExtractionService llmSkillExtractionService(
                SkillCandidateStore candidateStore,
                SkillDictionaryStore dictionaryStore,
                SkillEmbeddingPort embeddingPort,
                SkillMatchPolicy matchPolicy,
                PromptRenderer promptRenderer,
                ChatPort chatPort,
                SkillGraphProperties properties) {
            SkillGraphProperties.Extraction extraction = properties.getExtraction();
            SkillGraphProperties.Llm llm = extraction.getLlm();
            SkillCandidateExtractor extractor = new LlmSkillCandidateExtractor(
                    candidateStore,
                    dictionaryStore,
                    embeddingPort,
                    matchPolicy,
                    promptRenderer,
                    chatPort,
                    new ObjectMapper(),
                    llm.getPrompt(),
                    extraction.getMaxTerms(),
                    llm.getMaxInputChars(),
                    llm.getMaxOutputTokens(),
                    llm.getTemperature());
            return new DefaultSkillExtractionService(
                    candidateStore,
                    dictionaryStore,
                    extractor,
                    embeddingPort,
                    matchPolicy);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "studio.skillgraph.extraction", name = "mode", havingValue = "llm")
    @ConditionalOnMissingClass({
            "studio.one.platform.ai.core.chat.ChatPort",
            "studio.one.platform.ai.service.prompt.PromptRenderer"
    })
    static class MissingLlmExtractionConfig {

        @Bean(name = SkillExtractionService.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillExtractionService missingLlmSkillExtractionService() {
            throw new IllegalStateException(
                    "studio.skillgraph.extraction.mode=llm requires studio-platform-ai ChatPort and PromptRenderer");
        }
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

        @Bean(name = SkillRagExtractionJobStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillRagExtractionJobStore skillRagExtractionJobStore() {
            return new InMemorySkillRagExtractionJobStore();
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

        @Bean(name = SkillProjectionStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillProjectionStore skillProjectionStore(NamedParameterJdbcTemplate template) {
            return new JdbcSkillProjectionStore(template);
        }

        @Bean(name = SkillTaxonomyStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillTaxonomyStore skillTaxonomyStore(NamedParameterJdbcTemplate template) {
            return new JdbcSkillTaxonomyStore(template);
        }

        @Bean(name = SkillGraphStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillGraphStore skillGraphStore(NamedParameterJdbcTemplate template) {
            return new JdbcSkillGraphStore(template);
        }

        @Bean(name = SkillMappingStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillMappingStore skillMappingStore(NamedParameterJdbcTemplate template) {
            return new JdbcSkillMappingStore(template);
        }

        @Bean(name = SkillRagExtractionJobStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public SkillRagExtractionJobStore skillRagExtractionJobStore(NamedParameterJdbcTemplate template) {
            return new JdbcSkillRagExtractionJobStore(template);
        }
    }
}
