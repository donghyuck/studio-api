package studio.one.platform.ai.web.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRagAnswerCacheTest {

    @Test
    void writesVersionedKeyWithTtlAndReadsPayload() throws Exception {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Duration ttl = Duration.ofMinutes(15);
        RagCachedAnswer answer = answer(ttl);
        when(values.get("studio:ai:rag-answer:v1:digest"))
                .thenReturn(mapper.writeValueAsString(answer));
        RedisRagAnswerCache cache = new RedisRagAnswerCache(
                template, mapper, "studio:ai:rag-answer:v1", ttl, true);

        assertThat(cache.get(new RagAnswerCacheKey("digest"))).contains(answer);
        cache.put(new RagAnswerCacheKey("digest"), answer);

        verify(values).set(eq("studio:ai:rag-answer:v1:digest"), any(String.class), eq(ttl));
    }

    @Test
    void redisFailureIsMissWhenFailOpen() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        RedisRagAnswerCache cache = new RedisRagAnswerCache(
                template, new ObjectMapper(), "namespace", Duration.ofMinutes(1), true);

        assertThat(cache.get(new RagAnswerCacheKey("digest"))).isEmpty();
    }

    @Test
    void redisFailurePropagatesWhenFailClosed() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        RedisRagAnswerCache cache = new RedisRagAnswerCache(
                template, new ObjectMapper(), "namespace", Duration.ofMinutes(1), false);

        assertThatThrownBy(() -> cache.get(new RagAnswerCacheKey("digest")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cache read failed");
    }

    private RagCachedAnswer answer(Duration ttl) {
        Instant createdAt = Instant.parse("2026-07-25T00:00:00Z");
        return new RagCachedAnswer(
                "answer [1]",
                "model",
                "INDEX_VALID",
                "context",
                createdAt,
                createdAt.plus(ttl));
    }
}
