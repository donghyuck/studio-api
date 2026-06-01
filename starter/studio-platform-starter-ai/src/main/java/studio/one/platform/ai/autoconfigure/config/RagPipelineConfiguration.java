package studio.one.platform.ai.autoconfigure.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.rag.RagEmbeddingProfile;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.cleaning.LlmTextCleaner;
import studio.one.platform.ai.service.cleaning.TextCleaner;
import studio.one.platform.ai.service.chunk.OverlapTextChunker;
import studio.one.platform.ai.service.keyword.KeywordExtractor;
import studio.one.platform.ai.service.pipeline.DefaultRagPipelineService;
import studio.one.platform.ai.service.pipeline.DefaultRagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.DefaultRagIndexJobService;
import studio.one.platform.ai.service.pipeline.InMemoryRagIndexJobRepository;
import studio.one.platform.ai.service.pipeline.JdbcRagIndexJobRepository;
import studio.one.platform.ai.service.pipeline.RagIndexJobRepository;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceExecutor;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagKeywordOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineDiagnosticsOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.pipeline.SinglePortRagEmbeddingProfileResolver;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration(afterName = "studio.one.platform.chunking.autoconfigure.ChunkingAutoConfiguration")
@EnableConfigurationProperties({RagPipelineProperties.class, RagEmbeddingProperties.class, AiAdapterProperties.class})
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("deprecation")
public class RagPipelineConfiguration {

        private final ObjectProvider<I18n> i18nProvider;

        @Bean
        @ConditionalOnMissingBean({ TextChunker.class, ChunkingOrchestrator.class })
        public TextChunker textChunker(RagPipelineProperties properties) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n,  I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                                AiProviderRegistryConfiguration.FEATURE_NAME,
                                LogUtils.blue(RagPipelineService.class, true),
                                LogUtils.green(OverlapTextChunker.class, true),
                                LogUtils.red(State.CREATED.toString()) ));

                return new OverlapTextChunker(properties.getChunkSize(), properties.getChunkOverlap());
        }

        @Bean
        public Cache<String, List<Double>> embeddingCache(RagPipelineProperties properties) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n,  I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                                AiProviderRegistryConfiguration.FEATURE_NAME,
                                LogUtils.blue(RagPipelineService.class, true),
                                LogUtils.green(Caffeine.class, true),
                                LogUtils.red(State.CREATED.toString()) ));

                return Caffeine.newBuilder()
                                .maximumSize(properties.getCache().getMaximumSize())
                                .expireAfterWrite(properties.getCache().getTtl())
                                .build();
        }

        @Bean
        public Retry embeddingRetry(RagPipelineProperties properties) {
                Duration wait = properties.getRetry().getWaitDuration();
                RetryConfig config = RetryConfig.custom()
                                .maxAttempts(properties.getRetry().getMaxAttempts())
                                .waitDuration(wait)
                                .retryExceptions(RuntimeException.class)
                                .build();

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n,  I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                                AiProviderRegistryConfiguration.FEATURE_NAME,
                                LogUtils.blue(RagPipelineService.class, true),
                                LogUtils.green(Retry.class, true),
                                LogUtils.red(State.CREATED.toString()) ));
                                
                return Retry.of("embedding", config);
        }

        @Bean(name = { RagPipelineService.SERVICE_NAME, RagPipelineService.LEGACY_SERVICE_NAME })
        RagPipelineService ragPipelineService(EmbeddingPort embeddingPort, VectorStorePort vectorStorePort,
                        ObjectProvider<TextChunker> textChunkerProvider,
                        Cache<String, List<Double>> embeddingCache, Retry embeddingRetry,
                        ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
                        ObjectProvider<KeywordExtractor> keywordExtractorProvider,
                        ObjectProvider<TextCleaner> textCleanerProvider,
                        RagPipelineProperties properties,
                        RagEmbeddingProfileResolver embeddingProfileResolver) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                                AiProviderRegistryConfiguration.FEATURE_NAME,
                                LogUtils.blue(RagPipelineService.class, true), LogUtils.red(State.CREATED.toString())));

                return DefaultRagPipelineService.create(embeddingPort, vectorStorePort,
                                textChunkerProvider.getIfAvailable(),
                                chunkingOrchestratorProvider.getIfAvailable(), embeddingCache,
                                embeddingRetry, keywordExtractorProvider.getIfAvailable(),
                                textCleanerProvider.getIfAvailable(), ragPipelineOptions(properties),
                                ragPipelineDiagnosticsOptions(properties),
                                ragKeywordOptions(properties),
                                embeddingProfileResolver);
        }

        @Bean
        @ConditionalOnMissingBean(RagEmbeddingProfileResolver.class)
        RagEmbeddingProfileResolver ragEmbeddingProfileResolver(
                        EmbeddingPort embeddingPort,
                        ObjectProvider<AiProviderRegistry> providerRegistryProvider,
                        RagEmbeddingProperties properties,
                        AiAdapterProperties aiProperties,
                        Environment environment) {
                AiProviderRegistry providerRegistry = providerRegistryProvider.getIfAvailable();
                if (providerRegistry == null) {
                        return new SinglePortRagEmbeddingProfileResolver(embeddingPort);
                }
                return new DefaultRagEmbeddingProfileResolver(
                                embeddingPort,
                                providerRegistry,
                                properties.getDefaultEmbeddingProfile(),
                                ragEmbeddingProfiles(properties, aiProperties, environment));
        }

        @Bean
        @ConditionalOnBean(NamedParameterJdbcTemplate.class)
        @ConditionalOnMissingBean(RagIndexJobRepository.class)
        @Conditional(RagPipelineConditions.JdbcRepository.class)
        RagIndexJobRepository jdbcRagIndexJobRepository(NamedParameterJdbcTemplate template) {
                return new JdbcRagIndexJobRepository(template);
        }

        @Bean
        @ConditionalOnMissingBean(RagIndexJobRepository.class)
        RagIndexJobRepository ragIndexJobRepository() {
                return new InMemoryRagIndexJobRepository();
        }

        @Bean
        @ConditionalOnMissingBean(RagIndexJobService.class)
        RagIndexJobService ragIndexJobService(
                        RagIndexJobRepository ragIndexJobRepository,
                        RagPipelineService ragPipelineService,
                        ObjectProvider<RagIndexJobSourceExecutor> sourceExecutors) {
                return new DefaultRagIndexJobService(
                                ragIndexJobRepository,
                                ragPipelineService,
                                sourceExecutors.orderedStream().toList());
        }

        @Bean
        @ConditionalOnMissingBean(TextCleaner.class)
        @Conditional(RagPipelineConditions.CleanerEnabled.class)
        TextCleaner textCleaner(PromptRenderer promptRenderer,
                        ChatPort chatPort,
                        ObjectProvider<ObjectMapper> objectMapperProvider,
                        RagPipelineProperties properties) {
                RagPipelineProperties.CleanerProperties cleaner = properties.getCleaner();
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                                AiProviderRegistryConfiguration.FEATURE_NAME,
                                LogUtils.blue(LlmTextCleaner.class, true),
                                LogUtils.green(ChatPort.class, true),
                                LogUtils.red(State.CREATED.toString())));
                promptRenderer.getRawPrompt(cleaner.getPrompt());
                return new LlmTextCleaner(
                                promptRenderer,
                                chatPort,
                                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                                cleaner.getPrompt(),
                                cleaner.getMaxInputChars(),
                                cleaner.isFailOpen());
        }

        private Map<String, RagEmbeddingProfile> ragEmbeddingProfiles(
                        RagEmbeddingProperties properties,
                        AiAdapterProperties aiProperties,
                        Environment environment) {
                Map<String, RagEmbeddingProfile> profiles = new LinkedHashMap<>();
                properties.getEmbeddingProfiles().forEach((profileId, profile) -> {
                        String normalizedId = normalize(profileId);
                        if (normalizedId == null) {
                                return;
                        }
                        String providerId = normalize(profile.getProvider());
                        AiAdapterProperties.Provider provider = providerId == null
                                        ? null
                                        : aiProperties.getProviders().get(providerId);
                        profiles.put(normalizedId.toLowerCase(Locale.ROOT), new RagEmbeddingProfile(
                                        normalizedId,
                                        providerId,
                                        firstText(profile.getModel(), embeddingModel(providerId, provider, environment)),
                                        firstInteger(profile.getDimension(), embeddingDimension(providerId, provider, environment)),
                                        embeddingInputTypes(profile.getSupportedInputTypes()),
                                        profile.getMetadata()));
                });
                return profiles;
        }

        private String embeddingModel(
                        String providerId,
                        AiAdapterProperties.Provider provider,
                        Environment environment) {
                String providerModelKey = providerId == null
                                ? null
                                : "studio.ai.providers." + providerId + ".embedding.model";
                return switch (providerType(provider)) {
                        case "OPENAI" -> firstText(
                                        property(environment, "spring.ai.openai.embedding.options.model"),
                                        property(environment, providerModelKey),
                                        provider == null ? null : provider.getEmbedding().getModel());
                        case "GOOGLE_AI_GEMINI" -> firstText(
                                        property(environment, "spring.ai.google.genai.embedding.text.options.model"),
                                        property(environment, providerModelKey),
                                        provider == null ? null : provider.getEmbedding().getModel());
                        case "OLLAMA" -> firstText(
                                        property(environment, "spring.ai.ollama.embedding.options.model"),
                                        property(environment, providerModelKey),
                                        provider == null ? null : provider.getEmbedding().getModel());
                        default -> firstText(
                                        property(environment, providerModelKey),
                                        provider == null ? null : provider.getEmbedding().getModel());
                };
        }

        private Integer embeddingDimension(
                        String providerId,
                        AiAdapterProperties.Provider provider,
                        Environment environment) {
                String providerDimensionKey = providerId == null
                                ? null
                                : "studio.ai.providers." + providerId + ".embedding.dimension";
                Integer dimension = integerProperty(environment, providerDimensionKey);
                if (dimension != null) {
                        return dimension;
                }
                if ("GOOGLE_AI_GEMINI".equals(providerType(provider))) {
                        dimension = integerProperty(environment, "spring.ai.google.genai.embedding.text.options.dimensions");
                        if (dimension != null) {
                                return dimension;
                        }
                }
                return provider == null ? null : provider.getEmbedding().getDimension();
        }

        private String providerType(AiAdapterProperties.Provider provider) {
                return provider == null || provider.getType() == null ? "" : provider.getType().name();
        }

        private String property(Environment environment, String key) {
                return environment == null || key == null ? null : environment.getProperty(key);
        }

        private Integer integerProperty(Environment environment, String key) {
                return environment == null || key == null ? null : environment.getProperty(key, Integer.class);
        }

        private String firstText(String... values) {
                for (String value : values) {
                        String normalized = normalize(value);
                        if (normalized != null) {
                                return normalized;
                        }
                }
                return null;
        }

        private Integer firstInteger(Integer... values) {
                for (Integer value : values) {
                        if (value != null) {
                                return value;
                        }
                }
                return null;
        }

        private List<EmbeddingInputType> embeddingInputTypes(List<String> values) {
                if (values == null || values.isEmpty()) {
                        return List.of(EmbeddingInputType.TEXT);
                }
                List<EmbeddingInputType> inputTypes = values.stream()
                                .map(this::embeddingInputType)
                                .distinct()
                                .toList();
                return inputTypes.isEmpty() ? List.of(EmbeddingInputType.TEXT) : inputTypes;
        }

        private EmbeddingInputType embeddingInputType(String value) {
                if (value == null || value.isBlank()) {
                        return EmbeddingInputType.TEXT;
                }
                return EmbeddingInputType.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        }

        private String normalize(String value) {
                return value == null || value.isBlank() ? null : value.trim();
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
                                objectScope.getMaxListLimit(),
                                retrieval.getMaxContextTokens());
        }

        private RagPipelineDiagnosticsOptions ragPipelineDiagnosticsOptions(RagPipelineProperties properties) {
                RagPipelineProperties.DiagnosticsProperties diagnostics = properties.getDiagnostics();
                return new RagPipelineDiagnosticsOptions(
                                diagnostics.isEnabled(),
                                diagnostics.isLogResults(),
                                diagnostics.getMaxSnippetChars());
        }

        private RagKeywordOptions ragKeywordOptions(RagPipelineProperties properties) {
                RagPipelineProperties.KeywordsProperties keywords = properties.getKeywords();
                RagPipelineProperties.QueryExpansionProperties queryExpansion =
                                properties.getRetrieval().getQueryExpansion();
                return new RagKeywordOptions(
                                RagKeywordOptions.KeywordScope.from(keywords.getScope()),
                                keywords.getMaxInputChars(),
                                queryExpansion.isEnabled(),
                                queryExpansion.getMaxKeywords());
        }

}
