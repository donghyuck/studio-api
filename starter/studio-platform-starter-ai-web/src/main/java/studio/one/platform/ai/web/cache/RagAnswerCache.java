package studio.one.platform.ai.web.cache;

import java.time.Duration;
import java.util.Optional;

public interface RagAnswerCache {

    Optional<RagCachedAnswer> get(RagAnswerCacheKey key);

    void put(RagAnswerCacheKey key, RagCachedAnswer answer);

    default boolean enabled() {
        return true;
    }

    default Duration ttl() {
        return Duration.ZERO;
    }

    static RagAnswerCache noop() {
        return NoOpRagAnswerCache.INSTANCE;
    }
}
