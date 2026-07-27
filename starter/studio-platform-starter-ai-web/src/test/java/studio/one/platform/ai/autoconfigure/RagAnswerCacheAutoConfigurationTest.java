package studio.one.platform.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import studio.one.platform.ai.web.cache.CaffeineRagAnswerCache;
import studio.one.platform.ai.web.cache.RagAnswerCache;
import studio.one.platform.ai.web.cache.RedisRagAnswerCache;

class RagAnswerCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagAnswerCacheAutoConfiguration.class));

    @Test
    void defaultsToNoOpCache() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RagAnswerCache.class);
            assertThat(context.getBean(RagAnswerCache.class).enabled()).isFalse();
        });
    }

    @Test
    void defaultsToNoOpCacheWhenRedisIsNotOnTheClasspath() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RagAnswerCache.class);
                    assertThat(context.getBean(RagAnswerCache.class).enabled()).isFalse();
                });
    }

    @Test
    void createsCaffeineCacheFromProperties() {
        runner.withPropertyValues(
                        "studio.ai.rag.answer-cache.type=caffeine",
                        "studio.ai.rag.answer-cache.ttl=5m")
                .run(context -> {
                    assertThat(context).hasSingleBean(RagAnswerCache.class);
                    assertThat(context.getBean(RagAnswerCache.class))
                            .isInstanceOf(CaffeineRagAnswerCache.class);
                    assertThat(context.getBean(RagAnswerCache.class).ttl()).hasMinutes(5);
                });
    }

    @Test
    void createsRedisCacheOnlyWithRedisTemplate() {
        runner.withUserConfiguration(RedisTestConfiguration.class)
                .withPropertyValues("studio.ai.rag.answer-cache.type=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(RagAnswerCache.class);
                    assertThat(context.getBean(RagAnswerCache.class))
                            .isInstanceOf(RedisRagAnswerCache.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisTestConfiguration {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
