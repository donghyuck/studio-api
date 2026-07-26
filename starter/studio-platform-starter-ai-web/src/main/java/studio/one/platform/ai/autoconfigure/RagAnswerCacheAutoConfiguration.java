package studio.one.platform.ai.autoconfigure;

import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import studio.one.platform.ai.web.cache.CaffeineRagAnswerCache;
import studio.one.platform.ai.web.cache.RagAnswerCache;
import studio.one.platform.ai.web.cache.RedisRagAnswerCache;

@AutoConfiguration
@AutoConfigureBefore(AiWebAutoConfiguration.class)
@EnableConfigurationProperties(RagAnswerCacheProperties.class)
public class RagAnswerCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RagAnswerCache.class)
    @ConditionalOnProperty(
            prefix = "studio.ai.rag.answer-cache",
            name = "type",
            havingValue = "caffeine")
    RagAnswerCache caffeineRagAnswerCache(RagAnswerCacheProperties properties) {
        return new CaffeineRagAnswerCache(properties.getTtl());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    static class RedisConfiguration {

        @Bean
        @ConditionalOnBean(StringRedisTemplate.class)
        @ConditionalOnMissingBean(RagAnswerCache.class)
        @ConditionalOnProperty(
                prefix = "studio.ai.rag.answer-cache",
                name = "type",
                havingValue = "redis")
        RagAnswerCache redisRagAnswerCache(
                StringRedisTemplate redisTemplate,
                ObjectMapper objectMapper,
                RagAnswerCacheProperties properties) {
            return new RedisRagAnswerCache(
                    redisTemplate,
                    objectMapper,
                    properties.getNamespace(),
                    properties.getTtl(),
                    properties.isFailOpen());
        }
    }

    @Bean
    @ConditionalOnMissingBean(RagAnswerCache.class)
    @ConditionalOnProperty(
            prefix = "studio.ai.rag.answer-cache",
            name = "type",
            havingValue = "none",
            matchIfMissing = true)
    RagAnswerCache noOpRagAnswerCache() {
        return RagAnswerCache.noop();
    }
}
