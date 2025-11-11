package studio.one.platform.ai.service.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.service.chunk.OverlapTextChunker;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(RagPipelineProperties.class)
public class AiServiceConfiguration {

    @Bean
    public TextChunker textChunker(RagPipelineProperties properties) {
        return new OverlapTextChunker(properties.getChunkSize(), properties.getChunkOverlap());
    }

    @Bean
    public Cache<String, List<Double>> embeddingCache(RagPipelineProperties properties) {
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
        return Retry.of("embedding", config);
    }
}
