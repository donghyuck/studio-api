package studio.one.platform.ai.web.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisRagAnswerCacheIntegrationTest {

    private static final String NAMESPACE = "studio:test:ai:rag-answer:v2";
    private static final Duration TTL = Duration.ofSeconds(30);

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void roundTripsJackson3PayloadAndAppliesRedisTtl() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        RedisRagAnswerCache cache =
                new RedisRagAnswerCache(redisTemplate, objectMapper, NAMESPACE, TTL, true);
        RagAnswerCacheKey key = new RagAnswerCacheKey("integration");
        Instant createdAt = Instant.now();
        RagCachedAnswer answer = new RagCachedAnswer(
                "verified answer [1]",
                "chat-default",
                "INDEX_VALID",
                "context-fingerprint",
                createdAt,
                createdAt.plus(TTL));

        cache.put(key, answer);

        assertThat(cache.get(key)).contains(answer);
        assertThat(redisTemplate.getExpire(NAMESPACE + ":" + key.digest()))
                .isPositive()
                .isLessThanOrEqualTo(TTL.toSeconds());
        redisTemplate.delete(NAMESPACE + ":" + key.digest());
    }
}
