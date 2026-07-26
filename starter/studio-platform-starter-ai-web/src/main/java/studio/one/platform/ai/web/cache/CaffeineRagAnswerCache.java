package studio.one.platform.ai.web.cache;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public final class CaffeineRagAnswerCache implements RagAnswerCache {

    private final Cache<String, RagCachedAnswer> cache;
    private final Duration ttl;

    public CaffeineRagAnswerCache(Duration ttl) {
        this.ttl = ttl;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .build();
    }

    @Override
    public Optional<RagCachedAnswer> get(RagAnswerCacheKey key) {
        return Optional.ofNullable(cache.getIfPresent(key.digest()));
    }

    @Override
    public void put(RagAnswerCacheKey key, RagCachedAnswer answer) {
        cache.put(key.digest(), answer);
    }

    @Override
    public Duration ttl() {
        return ttl;
    }
}
