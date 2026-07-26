package studio.one.platform.ai.web.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
        ObjectMapper mapper = JsonMapper.builder().build();
        Duration ttl = Duration.ofMinutes(15);
        RagCachedAnswer answer = answer(ttl);
        when(values.get("studio:ai:rag-answer:v2:digest"))
                .thenReturn(mapper.writeValueAsString(answer));
        RedisRagAnswerCache cache = new RedisRagAnswerCache(
                template, mapper, "studio:ai:rag-answer:v2", ttl, true);

        assertThat(cache.get(new RagAnswerCacheKey("digest"))).contains(answer);
        cache.put(new RagAnswerCacheKey("digest"), answer);

        verify(values).set(eq("studio:ai:rag-answer:v2:digest"), any(String.class), eq(ttl));
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

    @Test
    void malformedPayloadIsMissWhenFailOpen() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get("namespace:digest")).thenReturn("{not-json");
        RedisRagAnswerCache cache = new RedisRagAnswerCache(
                template, JsonMapper.builder().build(), "namespace", Duration.ofMinutes(1), true);

        assertThat(cache.get(new RagAnswerCacheKey("digest"))).isEmpty();
    }

    @Test
    void redisWriteFailureIsIgnoredOnlyWhenFailOpen() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(values).set(eq("namespace:digest"), any(String.class), eq(Duration.ofMinutes(1)));
        RagCachedAnswer answer = answer(Duration.ofMinutes(1));

        RedisRagAnswerCache failOpen = new RedisRagAnswerCache(
                template, JsonMapper.builder().build(), "namespace", Duration.ofMinutes(1), true);
        failOpen.put(new RagAnswerCacheKey("digest"), answer);

        RedisRagAnswerCache failClosed = new RedisRagAnswerCache(
                template, JsonMapper.builder().build(), "namespace", Duration.ofMinutes(1), false);
        assertThatThrownBy(() -> failClosed.put(new RagAnswerCacheKey("digest"), answer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cache write failed");
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
