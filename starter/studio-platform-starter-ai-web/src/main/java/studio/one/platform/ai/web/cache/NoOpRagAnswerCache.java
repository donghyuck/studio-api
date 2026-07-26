package studio.one.platform.ai.web.cache;

import java.util.Optional;

final class NoOpRagAnswerCache implements RagAnswerCache {

    static final NoOpRagAnswerCache INSTANCE = new NoOpRagAnswerCache();

    private NoOpRagAnswerCache() {
    }

    @Override
    public Optional<RagCachedAnswer> get(RagAnswerCacheKey key) {
        return Optional.empty();
    }

    @Override
    public void put(RagAnswerCacheKey key, RagCachedAnswer answer) {
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
