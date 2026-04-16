package studio.one.platform.ai.autoconfigure.config;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.cleaning.LlmTextCleaner;
import studio.one.platform.ai.service.cleaning.TextCleaner;
import studio.one.platform.ai.service.chunk.OverlapTextChunker;
import studio.one.platform.ai.service.keyword.KeywordExtractor;
import studio.one.platform.ai.service.pipeline.DefaultRagPipelineService;
import studio.one.platform.ai.service.pipeline.RagKeywordOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineDiagnosticsOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration
@EnableConfigurationProperties(RagPipelineProperties.class)
@RequiredArgsConstructor
@Slf4j
public class RagPipelineConfiguration {

        private final ObjectProvider<I18n> i18nProvider;

        @Bean
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
                        TextChunker textChunker, Cache<String, List<Double>> embeddingCache, Retry embeddingRetry,
                        ObjectProvider<KeywordExtractor> keywordExtractorProvider,
                        ObjectProvider<TextCleaner> textCleanerProvider,
                        RagPipelineProperties properties) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                                AiProviderRegistryConfiguration.FEATURE_NAME,
                                LogUtils.blue(RagPipelineService.class, true), LogUtils.red(State.CREATED.toString())));

                return DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, embeddingCache,
                                embeddingRetry, keywordExtractorProvider.getIfAvailable(),
                                textCleanerProvider.getIfAvailable(), ragPipelineOptions(properties),
                                ragPipelineDiagnosticsOptions(properties),
                                ragKeywordOptions(properties));
        }

        @Bean
        @ConditionalOnMissingBean(TextCleaner.class)
        @ConditionalOnProperty(prefix = PropertyKeys.AI.PREFIX + ".pipeline.cleaner", name = "enabled", havingValue = "true")
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

        private RagPipelineOptions ragPipelineOptions(RagPipelineProperties properties) {
                RagPipelineProperties.RetrievalProperties retrieval = properties.getRetrieval();
                RagPipelineProperties.ObjectScopeProperties objectScope = properties.getObjectScope();
                return new RagPipelineOptions(
                                retrieval.getVectorWeight(),
                                retrieval.getLexicalWeight(),
                                retrieval.getMinRelevanceScore(),
                                retrieval.isKeywordFallbackEnabled(),
                                retrieval.isSemanticFallbackEnabled(),
                                objectScope.getDefaultListLimit(),
                                objectScope.getMaxListLimit());
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
