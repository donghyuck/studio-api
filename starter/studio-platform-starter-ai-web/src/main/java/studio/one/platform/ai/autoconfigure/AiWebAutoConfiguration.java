package studio.one.platform.ai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.autoconfigure.config.RagPipelineProperties;
import studio.one.platform.ai.core.chat.ChatMemoryStore;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ConversationRepositoryPort;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
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
import studio.one.platform.ai.service.visualization.VectorProjectionService;
import studio.one.platform.ai.service.visualization.VectorSearchVisualizationService;
import studio.one.platform.ai.web.controller.AiWebExceptionHandler;
import studio.one.platform.ai.web.controller.AiInfoController;
import studio.one.platform.ai.web.controller.ChatController;
import studio.one.platform.ai.web.controller.EmbeddingController;
import studio.one.platform.ai.web.controller.QueryRewriteController;
import studio.one.platform.ai.web.controller.RagChunkPreviewController;
import studio.one.platform.ai.web.controller.RagController;
import studio.one.platform.ai.web.controller.RagContextBuilder;
import studio.one.platform.ai.web.controller.RagIndexJobController;
import studio.one.platform.ai.web.controller.RagIndexJobEndpointSecurity;
import studio.one.platform.ai.web.controller.VectorController;
import studio.one.platform.ai.web.controller.VectorVisualizationMgmtController;
import studio.one.platform.ai.web.service.ConversationChatService;
import studio.one.platform.ai.web.service.InMemoryConversationRepository;
import studio.one.platform.ai.web.service.InMemoryChatMemoryStore;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceNameResolver;
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
@EnableConfigurationProperties({AiWebRagProperties.class, AiWebChatProperties.class, RagPipelineProperties.class})
public class AiWebAutoConfiguration {

    @Bean
    RagContextBuilder ragContextBuilder(
            AiWebRagProperties properties,
            ObjectProvider<ChunkContextExpander> contextExpanders) {
        return new RagContextBuilder(properties, contextExpanders.stream().toList());
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
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper aiWebObjectMapper() {
        return Jackson2ObjectMapperBuilder.json().build();
    }

    @Bean
    ChatController chatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            AiWebRagProperties ragProperties,
            AiWebChatProperties chatProperties,
            @Nullable ChatMemoryStore chatMemoryStore,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            RagPipelineProperties ragPipelineProperties) {
        return new ChatController(providerRegistry, ragPipelineService, ragContextBuilder,
                ragProperties.getDiagnostics().isAllowClientDebug(),
                chatMemoryStore,
                chatProperties.getMemory().isEnabled(),
                conversationChatService,
                objectMapper,
                ragProperties.getContext().getExpansion().getCandidateMultiplier(),
                ragProperties.getContext().getExpansion().getMaxCandidates(),
                ragPipelineOptions(ragPipelineProperties));
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
            ObjectProvider<VectorProjectionGenerator> generators) {
        return new DefaultVectorProjectionJobService(
                projectionRepository,
                pointRepository,
                itemRepository,
                generators.orderedStream().toList());
    }

    @Bean
    @ConditionalOnBean(VectorProjectionJobService.class)
    @ConditionalOnMissingBean
    VectorProjectionService vectorProjectionService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            VectorProjectionJobService jobService,
            @Qualifier("vectorProjectionExecutor") Executor vectorProjectionExecutor) {
        return new DefaultVectorProjectionService(
                projectionRepository,
                pointRepository,
                itemRepository,
                jobService,
                vectorProjectionExecutor);
    }

    @Bean
    @ConditionalOnBean({EmbeddingPort.class, VectorStorePort.class, VectorProjectionRepository.class,
            VectorProjectionPointRepository.class})
    @ConditionalOnMissingBean
    VectorSearchVisualizationService vectorSearchVisualizationService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository) {
        return new DefaultVectorSearchVisualizationService(
                embeddingPort,
                vectorStorePort,
                projectionRepository,
                pointRepository);
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
            ObjectProvider<RagIndexJobSourceNameResolver> sourceNameResolvers) {
        return new RagIndexJobController(
                ragIndexJobService,
                ragPipelineService,
                vectorStorePort,
                ragIndexJobExecutor,
                ragPipelineProperties.getObjectScope().getMaxListLimit(),
                sourceNameResolvers.orderedStream().toList());
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
    QueryRewriteController queryRewriteController(PromptRenderer promptManager, ChatPort chatPort) {
        return new QueryRewriteController(promptManager, chatPort);
    }

    @Bean
    AiInfoController aiInfoController(
            AiAdapterProperties properties,
            AiWebChatProperties chatProperties,
            Environment environment,
            @Nullable VectorStorePort vectorStorePort) {
        return new AiInfoController(properties, chatProperties, environment, vectorStorePort);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(NamedParameterJdbcTemplate.class)
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
