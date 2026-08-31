package studio.one.platform.ai.autoconfigure;

import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.autoconfigure.config.RagPipelineProperties;
import studio.one.platform.ai.core.chat.ChatMemoryStore;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ConversationRepositoryPort;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.model.ModelCatalog;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.PcaVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.TsneVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.UmapVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.ai.service.visualization.DefaultVectorProjectionJobService;
import studio.one.platform.ai.service.visualization.DefaultVectorProjectionService;
import studio.one.platform.ai.service.visualization.DefaultVectorSearchVisualizationService;
import studio.one.platform.ai.service.visualization.JdbcExistingVectorItemRepository;
import studio.one.platform.ai.service.visualization.JdbcVectorProjectionPointRepository;
import studio.one.platform.ai.service.visualization.JdbcVectorProjectionRepository;
import studio.one.platform.ai.service.visualization.VectorProjectionJobService;
import studio.one.platform.ai.service.visualization.VectorProjectionNotifier;
import studio.one.platform.ai.service.visualization.VectorProjectionService;
import studio.one.platform.ai.service.visualization.VectorSearchVisualizationService;
import studio.one.platform.ai.web.controller.AiWebExceptionHandler;
import studio.one.platform.ai.web.controller.AiInfoController;
import studio.one.platform.ai.web.controller.AiModelUsageController;
import studio.one.platform.ai.web.controller.AiModelUsageStore;
import studio.one.platform.ai.web.controller.AiPromptCacheMetricsRecorder;
import studio.one.platform.ai.web.controller.ChatController;
import studio.one.platform.ai.web.controller.EmbeddingController;
import studio.one.platform.ai.web.controller.InMemoryRagRetrievalEvaluationStore;
import studio.one.platform.ai.web.controller.InMemoryAiModelUsageStore;
import studio.one.platform.ai.web.controller.ModelCatalogController;
import studio.one.platform.ai.web.controller.MicrometerAiPromptCacheMetricsRecorder;
import studio.one.platform.ai.web.controller.InMemoryRagRetrievalEvaluationJobStore;
import studio.one.platform.ai.web.controller.InMemoryRagRetrievalEvaluationQuestionSetStore;
import studio.one.platform.ai.web.controller.JdbcRagRetrievalEvaluationStore;
import studio.one.platform.ai.web.controller.JdbcRagRetrievalEvaluationJobStore;
import studio.one.platform.ai.web.controller.JdbcRagRetrievalEvaluationQuestionSetStore;
import studio.one.platform.ai.web.controller.InMemoryRagRetrievalPolicyStore;
import studio.one.platform.ai.web.controller.InMemoryRagRetrievalPolicyHistoryStore;
import studio.one.platform.ai.web.controller.InMemoryRagRetrievalPolicyUsageStore;
import studio.one.platform.ai.web.controller.JdbcRagRetrievalPolicyStore;
import studio.one.platform.ai.web.controller.JdbcRagRetrievalPolicyHistoryStore;
import studio.one.platform.ai.web.controller.JdbcRagRetrievalPolicyUsageStore;
import studio.one.platform.ai.web.controller.QueryRewriteController;
import studio.one.platform.ai.web.controller.RagChunkPreviewController;
import studio.one.platform.ai.web.controller.RagChunkingSimulationController;
import studio.one.platform.ai.web.controller.RagController;
import studio.one.platform.ai.web.controller.RagChatRetrievalService;
import studio.one.platform.ai.web.controller.RagContextBuilder;
import studio.one.platform.ai.web.controller.RagAnswerFinalizer;
import studio.one.platform.ai.web.controller.RagAnswerPolicyResolver;
import studio.one.platform.ai.web.controller.RagAnswerPolicyValidator;
import studio.one.platform.ai.web.controller.RagAnswerPromptComposer;
import studio.one.platform.ai.web.controller.RagCitationValidator;
import studio.one.platform.ai.web.controller.RagExternalEvidenceService;
import studio.one.platform.ai.web.controller.RagIndexJobController;
import studio.one.platform.ai.web.controller.RagObjectAuthorizationRouter;
import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.core.rag.usability.RagObjectUsabilityEvidenceContributor;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceProvider;
import studio.one.platform.ai.web.controller.RagIndexJobEndpointSecurity;
import studio.one.platform.ai.web.controller.DocumentUsabilityController;
import studio.one.platform.ai.web.controller.DocumentUsabilityPolicyResolver;
import studio.one.platform.ai.web.controller.DocumentUsabilityService;
import studio.one.platform.ai.web.controller.DocumentAutoEvaluationController;
import studio.one.platform.ai.web.controller.DocumentAutoEvaluationService;
import studio.one.platform.ai.web.controller.DocumentRagEvaluationProjectionService;
import studio.one.platform.ai.web.controller.DocumentQuestionSuggestionController;
import studio.one.platform.ai.web.controller.DocumentQuestionSuggestionService;
import studio.one.platform.ai.web.controller.RagRetrievalEvaluationController;
import studio.one.platform.ai.web.controller.RagRetrievalEvaluationJobService;
import studio.one.platform.ai.web.controller.RagRetrievalEvaluationJobStore;
import studio.one.platform.ai.web.controller.RagRetrievalEvaluationQuestionSetStore;
import studio.one.platform.ai.web.controller.RagRetrievalPolicyController;
import studio.one.platform.ai.web.controller.RagRetrievalPolicyHistoryStore;
import studio.one.platform.ai.web.controller.RagRetrievalPolicyStore;
import studio.one.platform.ai.web.controller.RagRetrievalPolicyUsageStore;
import studio.one.platform.ai.web.controller.RagRetrievalRecommendationService;
import studio.one.platform.ai.web.controller.RagRetrievalEvaluationRunner;
import studio.one.platform.ai.web.controller.RagRetrievalEvaluationStore;
import studio.one.platform.ai.web.controller.RagSourcePolicyResolver;
import studio.one.platform.ai.web.controller.VectorController;
import studio.one.platform.ai.web.controller.VectorVisualizationMgmtController;
import studio.one.platform.ai.web.cache.RagAnswerCache;
import studio.one.platform.ai.web.service.ConversationChatService;
import studio.one.platform.ai.web.service.InMemoryConversationRepository;
import studio.one.platform.ai.web.service.InMemoryChatMemoryStore;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceNameResolver;
import studio.one.platform.ai.service.pipeline.RagObjectMetadataContributor;
import studio.one.platform.ai.service.pipeline.RagDocumentMetadataProvider;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkingOrchestrator;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "studio.one.platform.ai.core.chat.ChatPort",
        "jakarta.validation.Valid",
        "org.springframework.security.access.prepost.PreAuthorize",
        "org.springframework.web.bind.annotation.RestController"
})
@Conditional(AiWebEndpointCondition.class)
@EnableConfigurationProperties({
        AiWebRagProperties.class,
        AiWebChatProperties.class,
        AiModelUsageProperties.class,
        RagPipelineProperties.class,
        VectorProjectionProperties.class
})
public class AiWebAutoConfiguration {

    @Bean(name = "ragObjectAuthorizationRouter")
    RagObjectAuthorizationRouter ragObjectAuthorizationRouter(
            ApplicationContext applicationContext,
            ObjectProvider<RagObjectAuthorizer> authorizers) {
        return new RagObjectAuthorizationRouter(applicationContext, authorizers.orderedStream().toList());
    }

    @Bean
    RagContextBuilder ragContextBuilder(
            AiWebRagProperties properties,
            ObjectProvider<ChunkContextExpander> contextExpanders) {
        return new RagContextBuilder(properties, contextExpanders.stream().toList());
    }

    @Bean
    RagChatRetrievalService ragChatRetrievalService(
            RagPipelineService ragPipelineService,
            AiWebRagProperties properties,
            ObjectProvider<RagDocumentMetadataProvider> metadataProviders) {
        return new RagChatRetrievalService(
                ragPipelineService, properties.getRetrieval(), metadataProviders.stream().toList());
    }

    @Bean
    RagAnswerPolicyResolver ragAnswerPolicyResolver(AiWebRagProperties properties) {
        return new RagAnswerPolicyResolver(properties.getAnswerPolicy());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "studio.ai.endpoints.rag.external-sources",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(name = "officialEvidenceGatewayProvider")
    ExternalEvidenceProvider officialEvidenceGatewayProvider(
            AiWebRagProperties properties,
            ObjectMapper objectMapper) {
        return new OfficialEvidenceGatewayProvider(properties.getExternalSources(), objectMapper);
    }

    @Bean
    RagExternalEvidenceService ragExternalEvidenceService(
            ObjectProvider<ExternalEvidenceProvider> providers) {
        return new RagExternalEvidenceService(providers.orderedStream().toList());
    }

    @Bean
    RagSourcePolicyResolver ragSourcePolicyResolver(
            AiWebRagProperties properties,
            RagExternalEvidenceService externalEvidenceService) {
        return new RagSourcePolicyResolver(
                properties.getSourcePolicy(),
                externalEvidenceService.available());
    }

    @Bean
    RagAnswerPromptComposer ragAnswerPromptComposer() {
        return new RagAnswerPromptComposer();
    }

    @Bean
    RagAnswerFinalizer ragAnswerFinalizer(AiWebRagProperties properties) {
        return new RagAnswerFinalizer(
                new RagCitationValidator(),
                new RagAnswerPolicyValidator(),
                properties.getAnswerPolicy().isFactualListPartialAnswerEnabled());
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemoryStore.class)
    @ConditionalOnProperty(prefix = PropertyKeys.AI.Endpoints.PREFIX + ".chat.memory", name = "enabled", havingValue = "true")
    ChatMemoryStore chatMemoryStore(AiWebChatProperties properties) {
        return new InMemoryChatMemoryStore(properties.getMemory());
    }

    @Bean
    @ConditionalOnMissingBean(ConversationRepositoryPort.class)
    ConversationRepositoryPort conversationRepositoryPort() {
        return new InMemoryConversationRepository();
    }

    @Bean
    ConversationChatService conversationChatService(ConversationRepositoryPort repositoryPort) {
        return new ConversationChatService(repositoryPort);
    }

    @Bean
    ChatController chatController(
            ModelDeploymentRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagChatRetrievalService ragChatRetrievalService,
            RagContextBuilder ragContextBuilder,
            AiWebRagProperties ragProperties,
            AiWebChatProperties chatProperties,
            @Nullable ChatMemoryStore chatMemoryStore,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            RagPipelineProperties ragPipelineProperties,
            RagRetrievalPolicyStore ragRetrievalPolicyStore,
            RagRetrievalPolicyUsageStore ragRetrievalPolicyUsageStore,
            AiModelUsageStore modelUsageStore,
            ObjectProvider<RagAnswerCache> ragAnswerCacheProvider,
            RagAnswerPolicyResolver ragAnswerPolicyResolver,
            RagAnswerPromptComposer ragAnswerPromptComposer,
            RagAnswerFinalizer ragAnswerFinalizer,
            RagObjectAuthorizationRouter ragObjectAuthorizationRouter,
            RagSourcePolicyResolver ragSourcePolicyResolver,
            RagExternalEvidenceService ragExternalEvidenceService,
            ObjectProvider<DocumentQuestionSuggestionService> questionSuggestionService,
            ObjectProvider<IndexedRagSourceProvider> indexedRagSourceProviders) {
        ChatController controller = new ChatController(providerRegistry, ragPipelineService, ragChatRetrievalService,
                ragContextBuilder,
                ragProperties.getDiagnostics().isAllowClientDebug(),
                chatMemoryStore,
                chatProperties.getMemory().isEnabled(),
                conversationChatService,
                objectMapper,
                ragProperties.getContext().getExpansion().getCandidateMultiplier(),
                ragProperties.getContext().getExpansion().getMaxCandidates(),
                ragPipelineOptions(ragPipelineProperties),
                ragRetrievalPolicyStore,
                ragRetrievalPolicyUsageStore,
                modelUsageStore,
                ragAnswerCacheProvider.getIfAvailable(RagAnswerCache::noop),
                ragAnswerPolicyResolver,
                ragAnswerPromptComposer,
                ragAnswerFinalizer,
                ragObjectAuthorizationRouter,
                ragSourcePolicyResolver,
                ragExternalEvidenceService);
        controller.setIndexedRagSourceProviders(indexedRagSourceProviders.orderedStream().toList());
        controller.setQuestionSuggestionsEnabled(questionSuggestionService.getIfAvailable() != null);
        return controller;
    }

    @Bean
    @ConditionalOnBean({ModelCatalog.class, ModelDeploymentRegistry.class})
    ModelCatalogController modelCatalogController(
            ModelCatalog catalog,
            ModelDeploymentRegistry registry,
            AiAdapterProperties adapterProperties) {
        return new ModelCatalogController(catalog, registry, adapterProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    AiModelUsageStore aiModelUsageStore(
            AiModelUsageProperties properties,
            AiPromptCacheMetricsRecorder metricsRecorder) {
        return new InMemoryAiModelUsageStore(properties, metricsRecorder);
    }

    @Bean
    @ConditionalOnClass(io.micrometer.core.instrument.MeterRegistry.class)
    @ConditionalOnBean(io.micrometer.core.instrument.MeterRegistry.class)
    @ConditionalOnMissingBean(AiPromptCacheMetricsRecorder.class)
    AiPromptCacheMetricsRecorder micrometerAiPromptCacheMetricsRecorder(
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new MicrometerAiPromptCacheMetricsRecorder(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(AiPromptCacheMetricsRecorder.class)
    AiPromptCacheMetricsRecorder aiPromptCacheMetricsRecorder() {
        return AiPromptCacheMetricsRecorder.noop();
    }

    @Bean
    AiModelUsageController aiModelUsageController(AiModelUsageStore usageStore) {
        return new AiModelUsageController(usageStore);
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalEvaluationStore ragRetrievalEvaluationStore(
            ApplicationContext context,
            ObjectMapper objectMapper) {
        Object jdbcTemplate = jdbcTemplate(context);
        if (jdbcTemplate == null) {
            return new InMemoryRagRetrievalEvaluationStore();
        }
        return new JdbcRagRetrievalEvaluationStore((NamedParameterJdbcTemplate) jdbcTemplate, objectMapper);
    }

    @Nullable
    private Object jdbcTemplate(ApplicationContext context) {
        if (!isClassPresent("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")) {
            return null;
        }
        Class<?> jdbcTemplateType;
        try {
            jdbcTemplateType = Class.forName("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
                    false, getClass().getClassLoader());
        } catch (ClassNotFoundException ex) {
            return null;
        }
        String[] names = context.getBeanNamesForType(jdbcTemplateType, false, false);
        if (names.length == 0) {
            return null;
        }
        return context.getBean(names[0]);
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    @Bean
    @ConditionalOnMissingBean(name = "ragRetrievalEvaluationExecutor")
    Executor ragRetrievalEvaluationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rag-eval-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalEvaluationRunner ragRetrievalEvaluationRunner(
            RagChatRetrievalService retrievalService,
            RagRetrievalEvaluationStore evaluationStore) {
        return new RagRetrievalEvaluationRunner(retrievalService, evaluationStore);
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalEvaluationQuestionSetStore ragRetrievalEvaluationQuestionSetStore(
            ApplicationContext context,
            ObjectMapper objectMapper) {
        Object jdbcTemplate = jdbcTemplate(context);
        if (jdbcTemplate == null) {
            return new InMemoryRagRetrievalEvaluationQuestionSetStore();
        }
        return new JdbcRagRetrievalEvaluationQuestionSetStore((NamedParameterJdbcTemplate) jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalEvaluationJobStore ragRetrievalEvaluationJobStore(ApplicationContext context) {
        Object jdbcTemplate = jdbcTemplate(context);
        if (jdbcTemplate == null) {
            return new InMemoryRagRetrievalEvaluationJobStore();
        }
        return new JdbcRagRetrievalEvaluationJobStore((NamedParameterJdbcTemplate) jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalPolicyStore ragRetrievalPolicyStore(
            ApplicationContext context,
            ObjectMapper objectMapper) {
        Object jdbcTemplate = jdbcTemplate(context);
        if (jdbcTemplate == null) {
            return new InMemoryRagRetrievalPolicyStore();
        }
        return new JdbcRagRetrievalPolicyStore((NamedParameterJdbcTemplate) jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalPolicyUsageStore ragRetrievalPolicyUsageStore(ApplicationContext context) {
        Object jdbcTemplate = jdbcTemplate(context);
        if (jdbcTemplate == null) {
            return new InMemoryRagRetrievalPolicyUsageStore();
        }
        return new JdbcRagRetrievalPolicyUsageStore((NamedParameterJdbcTemplate) jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalPolicyHistoryStore ragRetrievalPolicyHistoryStore(ApplicationContext context) {
        Object jdbcTemplate = jdbcTemplate(context);
        if (jdbcTemplate == null) {
            return new InMemoryRagRetrievalPolicyHistoryStore();
        }
        return new JdbcRagRetrievalPolicyHistoryStore((NamedParameterJdbcTemplate) jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalRecommendationService ragRetrievalRecommendationService() {
        return new RagRetrievalRecommendationService();
    }

    @Bean
    @ConditionalOnMissingBean
    RagRetrievalEvaluationJobService ragRetrievalEvaluationJobService(
            RagRetrievalEvaluationRunner evaluationRunner,
            @Qualifier("ragRetrievalEvaluationExecutor") Executor executor,
            RagRetrievalEvaluationJobStore jobStore) {
        return new RagRetrievalEvaluationJobService(evaluationRunner, executor, jobStore);
    }

    @Bean
    RagRetrievalEvaluationController ragRetrievalEvaluationController(
            RagRetrievalEvaluationRunner evaluationRunner,
            RagRetrievalEvaluationStore evaluationStore,
            RagRetrievalEvaluationJobService jobService,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            RagRetrievalRecommendationService recommendationService) {
        return new RagRetrievalEvaluationController(evaluationRunner, evaluationStore, jobService, questionSetStore,
                recommendationService);
    }

    @Bean
    RagRetrievalPolicyController ragRetrievalPolicyController(
            RagRetrievalPolicyStore policyStore,
            RagRetrievalEvaluationStore evaluationStore,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            RagRetrievalRecommendationService recommendationService,
            AiWebRagProperties ragProperties,
            RagRetrievalPolicyUsageStore usageStore,
            RagRetrievalPolicyHistoryStore historyStore) {
        return new RagRetrievalPolicyController(policyStore, evaluationStore, questionSetStore, recommendationService,
                ragProperties.getRetrieval(), usageStore, historyStore);
    }

    @Bean
    AiWebExceptionHandler aiWebExceptionHandler() {
        return new AiWebExceptionHandler();
    }

    @Bean
    EmbeddingController embeddingController(EmbeddingPort embeddingPort) {
        return new EmbeddingController(embeddingPort);
    }

    @Bean
    VectorController vectorController(
            EmbeddingPort embeddingPort,
            @Nullable RagEmbeddingProfileResolver embeddingProfileResolver,
            @Nullable VectorStorePort vectorStorePort,
            RagPipelineProperties ragPipelineProperties) {
        return new VectorController(embeddingPort, embeddingProfileResolver, vectorStorePort,
                ragPipelineOptions(ragPipelineProperties));
    }

    @Bean
    @Primary
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnMissingBean
    PcaVectorProjectionGenerator pcaVectorProjectionGenerator() {
        return new PcaVectorProjectionGenerator();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnMissingBean
    UmapVectorProjectionGenerator umapVectorProjectionGenerator() {
        return new UmapVectorProjectionGenerator();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnMissingBean
    TsneVectorProjectionGenerator tsneVectorProjectionGenerator() {
        return new TsneVectorProjectionGenerator();
    }

    @Bean(name = "vectorProjectionExecutor")
    @ConditionalOnMissingBean(name = "vectorProjectionExecutor")
    Executor vectorProjectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("vector-projection-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnBean({VectorProjectionRepository.class, VectorProjectionPointRepository.class, ExistingVectorItemRepository.class})
    @ConditionalOnMissingBean
    VectorProjectionJobService vectorProjectionJobService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            ObjectProvider<VectorProjectionGenerator> generators,
            ObjectProvider<VectorProjectionNotifier> projectionNotifierProvider) {
        return new DefaultVectorProjectionJobService(
                projectionRepository,
                pointRepository,
                itemRepository,
                generators.orderedStream().toList(),
                projectionNotifierProvider.getIfAvailable(() -> VectorProjectionNotifier.NOOP));
    }

    @Bean
    @ConditionalOnClass(name = "studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService")
    @ConditionalOnBean(type = "studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService")
    @ConditionalOnMissingBean(VectorProjectionNotifier.class)
    VectorProjectionNotifier vectorProjectionNotifier(ApplicationContext context) {
        try {
            Class<?> messagingType = Class.forName("studio.one.platform.realtime.stomp.messaging.RealtimeMessagingService");
            Object messagingService = context.getBean(messagingType);
            Class<?> notifierType = Class.forName(
                    "studio.one.platform.ai.autoconfigure.realtime.StompVectorProjectionNotifier");
            return (VectorProjectionNotifier) notifierType
                    .getConstructor(messagingType)
                    .newInstance(messagingService);
        } catch (ReflectiveOperationException ex) {
            throw new BeanInstantiationException(VectorProjectionNotifier.class,
                    "Failed to create STOMP vector projection notifier", ex);
        }
    }

    @Bean
    @ConditionalOnBean(VectorProjectionJobService.class)
    @ConditionalOnMissingBean
    VectorProjectionService vectorProjectionService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            VectorProjectionJobService jobService,
            @Qualifier("vectorProjectionExecutor") Executor vectorProjectionExecutor,
            VectorProjectionProperties properties) {
        return new DefaultVectorProjectionService(
                projectionRepository,
                pointRepository,
                itemRepository,
                jobService,
                vectorProjectionExecutor,
                properties.getMaxItems(),
                properties.getDefaultSampleSize(),
                properties.getDefaultSamplingStrategy(),
                properties.getProcessingTimeout());
    }

    @Bean
    @ConditionalOnBean({EmbeddingPort.class, ModelDeploymentRegistry.class, VectorStorePort.class, VectorProjectionRepository.class,
            VectorProjectionPointRepository.class, ExistingVectorItemRepository.class})
    @ConditionalOnMissingBean
    VectorSearchVisualizationService vectorSearchVisualizationService(
            EmbeddingPort embeddingPort,
            ModelDeploymentRegistry providerRegistry,
            VectorStorePort vectorStorePort,
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository) {
        return new DefaultVectorSearchVisualizationService(
                embeddingPort,
                vectorStorePort,
                projectionRepository,
                pointRepository,
                itemRepository,
                providerRegistry);
    }

    @Bean
    @ConditionalOnBean(VectorProjectionService.class)
    VectorVisualizationMgmtController vectorVisualizationMgmtController(
            VectorProjectionService projectionService,
            @Nullable VectorSearchVisualizationService searchVisualizationService) {
        return new VectorVisualizationMgmtController(projectionService, searchVisualizationService);
    }

    @Bean
    RagController ragController(
            RagPipelineService ragPipelineService,
            @Nullable RagIndexJobService ragIndexJobService,
            RagPipelineProperties ragPipelineProperties) {
        return new RagController(ragPipelineService, ragIndexJobService, ragPipelineOptions(ragPipelineProperties));
    }

    private RagPipelineOptions ragPipelineOptions(RagPipelineProperties properties) {
        RagPipelineProperties.RetrievalProperties retrieval = properties.getRetrieval();
        RagPipelineProperties.ObjectScopeProperties objectScope = properties.getObjectScope();
        return new RagPipelineOptions(
                retrieval.getVectorWeight(),
                retrieval.getLexicalWeight(),
                retrieval.getMinScore(),
                retrieval.getMinRelevanceScore(),
                retrieval.isKeywordFallbackEnabled(),
                retrieval.isSemanticFallbackEnabled(),
                retrieval.getTopK(),
                objectScope.getDefaultListLimit(),
                objectScope.getMaxListLimit());
    }

    @Bean
    @ConditionalOnBean(RagIndexJobService.class)
    RagIndexJobController ragIndexJobController(
            RagIndexJobService ragIndexJobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort,
            RagPipelineProperties ragPipelineProperties,
            @Qualifier("ragIndexJobExecutor") Executor ragIndexJobExecutor,
            ObjectProvider<RagIndexJobSourceNameResolver> sourceNameResolvers,
            ObjectProvider<RagObjectMetadataContributor> metadataContributors) {
        return new RagIndexJobController(
                ragIndexJobService,
                ragPipelineService,
                vectorStorePort,
                ragIndexJobExecutor,
                ragPipelineProperties.getObjectScope().getMaxListLimit(),
                sourceNameResolvers.orderedStream().toList(),
                metadataContributors.orderedStream().toList());
    }

    @Bean
    DocumentUsabilityPolicyResolver documentUsabilityPolicyResolver() {
        return new DocumentUsabilityPolicyResolver();
    }

    @Bean
    @ConditionalOnBean(RagIndexJobService.class)
    DocumentUsabilityService documentUsabilityService(
            RagIndexJobService ragIndexJobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort,
            ObjectProvider<RagObjectUsabilityEvidenceContributor> evidenceContributors,
            DocumentUsabilityPolicyResolver policyResolver,
            DocumentRagEvaluationProjectionService evaluationProjectionService) {
        return new DocumentUsabilityService(
                ragIndexJobService,
                ragPipelineService,
                vectorStorePort,
                evidenceContributors.orderedStream().toList(),
                policyResolver,
                evaluationProjectionService);
    }

    @Bean
    @ConditionalOnBean(DocumentUsabilityService.class)
    DocumentUsabilityController documentUsabilityController(DocumentUsabilityService service) {
        return new DocumentUsabilityController(service);
    }

    @Bean
    DocumentRagEvaluationProjectionService documentRagEvaluationProjectionService(
            RagRetrievalEvaluationStore evaluationStore,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            ObjectMapper objectMapper) {
        return new DocumentRagEvaluationProjectionService(evaluationStore, questionSetStore, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DocumentUsabilityService.class, VectorStorePort.class})
    DocumentAutoEvaluationService documentAutoEvaluationService(
            DocumentUsabilityService usabilityService,
            VectorStorePort vectorStorePort,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            RagRetrievalEvaluationRunner evaluationRunner,
            DocumentRagEvaluationProjectionService projectionService) {
        return new DocumentAutoEvaluationService(
                usabilityService, vectorStorePort, questionSetStore, evaluationRunner, projectionService);
    }

    @Bean
    @ConditionalOnBean(DocumentAutoEvaluationService.class)
    DocumentAutoEvaluationController documentAutoEvaluationController(DocumentAutoEvaluationService service) {
        return new DocumentAutoEvaluationController(service);
    }

    @Bean
    @ConditionalOnBean({DocumentUsabilityService.class, VectorStorePort.class})
    DocumentQuestionSuggestionService documentQuestionSuggestionService(
            DocumentUsabilityService usabilityService,
            VectorStorePort vectorStorePort,
            ObjectProvider<RagObjectMetadataContributor> metadataContributors) {
        return new DocumentQuestionSuggestionService(
                usabilityService,
                vectorStorePort,
                metadataContributors.orderedStream().toList());
    }

    @Bean
    @ConditionalOnBean(DocumentQuestionSuggestionService.class)
    DocumentQuestionSuggestionController documentQuestionSuggestionController(
            DocumentQuestionSuggestionService service) {
        return new DocumentQuestionSuggestionController(service);
    }

    @Bean
    @SuppressWarnings("deprecation")
    RagChunkPreviewController ragChunkPreviewController(
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<Chunker> chunkers,
            ObjectProvider<TextChunker> textChunkerProvider,
            RagPipelineProperties ragPipelineProperties,
            AiWebRagProperties ragProperties,
            Environment environment) {
        return new RagChunkPreviewController(
                chunkingOrchestratorProvider.getIfAvailable(),
                chunkers.stream().toList(),
                textChunkerProvider.getIfAvailable(),
                ragPipelineProperties,
                ragProperties,
                environment);
    }

    @Bean
    RagChunkingSimulationController ragChunkingSimulationController(
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            AiWebRagProperties ragProperties,
            Environment environment,
            ObjectProvider<RagPipelineService> ragPipelineServiceProvider) {
        return new RagChunkingSimulationController(
                chunkingOrchestratorProvider.getIfAvailable(),
                ragProperties,
                environment,
                ragPipelineServiceProvider.getIfAvailable());
    }

    @Bean(name = "ragIndexJobEndpointSecurity")
    @ConditionalOnBean(RagIndexJobService.class)
    @ConditionalOnMissingBean(name = "ragIndexJobEndpointSecurity")
    RagIndexJobEndpointSecurity ragIndexJobEndpointSecurity(RagIndexJobService ragIndexJobService) {
        return new RagIndexJobEndpointSecurity(ragIndexJobService);
    }

    @Bean(name = "ragIndexJobExecutor")
    @ConditionalOnBean(RagIndexJobService.class)
    @ConditionalOnMissingBean(name = "ragIndexJobExecutor")
    Executor ragIndexJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rag-index-job-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean
    QueryRewriteController queryRewriteController(
            PromptRenderer promptManager,
            ChatPort chatPort,
            ObjectMapper objectMapper) {
        return new QueryRewriteController(promptManager, chatPort, objectMapper);
    }

    @Bean
    AiInfoController aiInfoController(
            AiAdapterProperties properties,
            AiWebChatProperties chatProperties,
            Environment environment,
            ModelDeploymentRegistry deploymentRegistry,
            @Nullable VectorStorePort vectorStorePort) {
        return new AiInfoController(properties, chatProperties, environment, vectorStorePort, deploymentRegistry);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
            "studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository",
            "studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository",
            "studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository",
            "studio.one.platform.ai.service.visualization.JdbcExistingVectorItemRepository",
            "studio.one.platform.ai.service.visualization.JdbcVectorProjectionRepository",
            "studio.one.platform.ai.service.visualization.JdbcVectorProjectionPointRepository"
    })
    static class VectorProjectionJdbcConfiguration {

        @Bean
        @ConditionalOnBean(NamedParameterJdbcTemplate.class)
        @ConditionalOnMissingBean
        ExistingVectorItemRepository existingVectorItemRepository(
                NamedParameterJdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper) {
            return new JdbcExistingVectorItemRepository(jdbcTemplate, objectMapper);
        }

        @Bean
        @ConditionalOnBean(NamedParameterJdbcTemplate.class)
        @ConditionalOnMissingBean
        VectorProjectionRepository vectorProjectionRepository(
                NamedParameterJdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper) {
            return new JdbcVectorProjectionRepository(jdbcTemplate, objectMapper);
        }

        @Bean
        @ConditionalOnBean(NamedParameterJdbcTemplate.class)
        @ConditionalOnMissingBean
        VectorProjectionPointRepository vectorProjectionPointRepository(
                NamedParameterJdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper) {
            return new JdbcVectorProjectionPointRepository(jdbcTemplate, objectMapper);
        }
    }
}
