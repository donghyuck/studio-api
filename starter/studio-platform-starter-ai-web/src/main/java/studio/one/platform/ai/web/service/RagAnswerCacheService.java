package studio.one.platform.ai.web.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import studio.one.platform.ai.web.cache.RagAnswerCache;
import studio.one.platform.ai.web.cache.RagAnswerCacheKey;
import studio.one.platform.ai.web.cache.RagCachedAnswer;
import studio.one.platform.ai.web.controller.PackedEvidenceSet;
import studio.one.platform.ai.web.controller.RagAnswerFinalizer;
import studio.one.platform.ai.web.controller.RagCitationValidator;
import studio.one.platform.ai.web.controller.RagQueryIntentClassifier;
import studio.one.platform.ai.web.controller.ResolvedRagAnswerPolicy;

/** Cache access after the caller has resolved identity, authorization and cache eligibility. */
public final class RagAnswerCacheService {
    private final RagAnswerCache cache;
    private final RagAnswerFinalizer finalizer;

    public RagAnswerCacheService(RagAnswerCache cache, RagAnswerFinalizer finalizer) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.finalizer = Objects.requireNonNull(finalizer, "finalizer");
    }

    public Optional<RagCachedAnswer> get(RagAnswerCacheKey key, PackedEvidenceSet evidence,
            ResolvedRagAnswerPolicy policy, RagQueryIntentClassifier.Classification intent) {
        return cache.get(key)
                .filter(answer -> answer.isValidFor(evidence.contextFingerprint(), policy.fingerprint(), Instant.now()))
                .filter(answer -> RagCitationValidator.Status.INDEX_VALID.name().equals(answer.citationValidationStatus()))
                .filter(answer -> {
                    var validated = finalizer.finalizeAnswer(answer.canonicalContent(), evidence, policy, intent);
                    return validated.validation().valid() && validated.canonicalContent().equals(answer.canonicalContent());
                });
    }

    public void put(RagAnswerCacheKey key, String model, PackedEvidenceSet evidence,
            ResolvedRagAnswerPolicy policy, RagAnswerFinalizer.FinalizedAnswer answer) {
        if (!answer.validation().valid() || !answer.policyValidation().valid() || answer.canonicalContent().isBlank()) {
            return;
        }
        Instant now = Instant.now();
        cache.put(key, new RagCachedAnswer(answer.canonicalContent(), model, answer.validation().status().name(),
                evidence.contextFingerprint(), policy.effectiveMode().name(), policy.fingerprint(),
                answer.policyValidation().status().name(), answer.outcome().partial(),
                answer.outcome().originalValidationUnitCount(), answer.outcome().omittedValidationUnitCount(),
                now, now.plus(cache.ttl())));
    }
}
