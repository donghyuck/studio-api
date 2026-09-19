package studio.one.platform.ai.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.web.cache.RagAnswerCache;
import studio.one.platform.ai.web.cache.RagAnswerCacheKey;
import studio.one.platform.ai.web.cache.RagCachedAnswer;
import studio.one.platform.ai.web.controller.PackedEvidenceSet;
import studio.one.platform.ai.web.controller.RagAnswerFinalizer;
import studio.one.platform.ai.web.controller.RagAnswerPolicyResolver;

class RagAnswerCacheServiceTest {
    private final RagAnswerCache cache = mock(RagAnswerCache.class);
    private final RagAnswerFinalizer finalizer = new RagAnswerFinalizer();
    private final RagAnswerCacheService service = new RagAnswerCacheService(cache, finalizer);
    private final RagAnswerCacheKey key = new RagAnswerCacheKey("authorized-scope-key");
    private final PackedEvidenceSet evidence = PackedEvidenceSet.from("context",
            List.of(new RagSearchResult("doc", "검증된 문서의 충분히 긴 설명입니다.", Map.of("chunkId", "c1"), 1)), Map.of());
    private final studio.one.platform.ai.web.controller.ResolvedRagAnswerPolicy policy = RagAnswerPolicyResolver.defaults().resolve(null);
    private final String content = "검증된 문서의 충분히 긴 설명입니다. [1]";

    @Test
    void storesValidatedAnswerAndRevalidatesHit() {
        when(cache.ttl()).thenReturn(Duration.ofMinutes(2));
        var answer = finalizer.finalizeAnswer(content, evidence, policy, null);
        assertThat(answer.validation().valid()).isTrue();
        service.put(key, "model", evidence, policy, answer);
        var stored = ArgumentCaptor.forClass(RagCachedAnswer.class);
        verify(cache).put(eq(key), stored.capture());
        assertThat(Duration.between(stored.getValue().createdAt(), stored.getValue().expiresAt())).isEqualTo(Duration.ofMinutes(2));
        when(cache.get(key)).thenReturn(Optional.of(stored.getValue()));
        assertThat(service.get(key, evidence, policy, null)).contains(stored.getValue());
    }

    @Test
    void rejectsExpiredDifferentEvidenceAndInvalidCitations() {
        for (RagCachedAnswer answer : List.of(cached(content, "different-evidence", Instant.now().plusSeconds(60)),
                cached(content, evidence.contextFingerprint(), Instant.now().minusSeconds(1)),
                cached("검증되지 않은 인용입니다. [999]", evidence.contextFingerprint(), Instant.now().plusSeconds(60)))) {
            when(cache.get(key)).thenReturn(Optional.of(answer));
            assertThat(service.get(key, evidence, policy, null)).isEmpty();
        }
    }

    @Test
    void doesNotStoreUnvalidatedAnswer() {
        service.put(key, "model", evidence, policy, finalizer.finalizeAnswer("근거 없는 답변입니다.", evidence, policy, null));
        verifyNoInteractions(cache);
    }

    private RagCachedAnswer cached(String text, String fingerprint, Instant expiresAt) {
        return new RagCachedAnswer(text, "model", "INDEX_VALID", fingerprint, policy.effectiveMode().name(),
                policy.fingerprint(), "VALID", false, 1, 0, Instant.now().minusSeconds(10), expiresAt);
    }
}
