package studio.one.platform.ai.autoconfigure.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.chunk.OverlapTextChunker;
import studio.one.platform.ai.service.keyword.KeywordExtractor;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
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

        @Bean(RagPipelineService.SERVICE_NAME)
        RagPipelineService ragPipelineService(EmbeddingPort embeddingPort, VectorStorePort vectorStorePort,
                        TextChunker textChunker, Cache<String, List<Double>> embeddingCache, Retry embeddingRetry,
                        ObjectProvider<KeywordExtractor> keywordExtractorProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                                AiProviderRegistryConfiguration.FEATURE_NAME,
                                LogUtils.blue(RagPipelineService.class, true), LogUtils.red(State.CREATED.toString())));

                return new RagPipelineService(embeddingPort, vectorStorePort, textChunker, embeddingCache,
                                embeddingRetry, keywordExtractorProvider.getIfAvailable());
        }

}
