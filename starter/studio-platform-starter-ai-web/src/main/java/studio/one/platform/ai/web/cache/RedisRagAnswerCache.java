package studio.one.platform.ai.web.cache;

import java.time.Duration;
import java.util.Optional;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisRagAnswerCache implements RagAnswerCache {

    private static final Logger log = LoggerFactory.getLogger(RedisRagAnswerCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String namespace;
    private final Duration ttl;
    private final boolean failOpen;

    public RedisRagAnswerCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            String namespace,
            Duration ttl,
            boolean failOpen) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.namespace = namespace;
        this.ttl = ttl;
        this.failOpen = failOpen;
    }

    @Override
    public Optional<RagCachedAnswer> get(RagAnswerCacheKey key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            String payload = redisTemplate.opsForValue().get(redisKey(key));
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, RagCachedAnswer.class));
        } catch (RuntimeException ex) {
            return onFailure("read", ex);
        }
    }

    @Override
    public void put(RagAnswerCacheKey key, RagCachedAnswer answer) {
        if (key == null || answer == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    redisKey(key),
                    objectMapper.writeValueAsString(answer),
                    ttl);
        } catch (RuntimeException ex) {
            onFailure("write", ex);
        }
    }

    @Override
    public Duration ttl() {
        return ttl;
    }

    private Optional<RagCachedAnswer> onFailure(String operation, Exception ex) {
        if (!failOpen) {
            throw new IllegalStateException("RAG answer cache " + operation + " failed", ex);
        }
        log.warn("RAG answer cache {} failed; continuing without cache: errorType={}",
                operation, ex.getClass().getSimpleName());
        return Optional.empty();
    }

    private String redisKey(RagAnswerCacheKey key) {
        return namespace + ":" + key.digest();
    }
}
