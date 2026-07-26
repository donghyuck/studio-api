package studio.one.platform.ai.web.cache;

import java.time.Instant;

public record RagCachedAnswer(
        String canonicalContent,
        String model,
        String citationValidationStatus,
        String contextFingerprint,
        Instant createdAt,
        Instant expiresAt) {

    public RagCachedAnswer {
        if (canonicalContent == null || canonicalContent.isBlank()) {
            throw new IllegalArgumentException("canonicalContent must not be blank");
        }
        model = model == null ? "" : model;
        citationValidationStatus = citationValidationStatus == null ? "" : citationValidationStatus;
        contextFingerprint = contextFingerprint == null ? "" : contextFingerprint;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        expiresAt = expiresAt == null ? createdAt : expiresAt;
    }

    public boolean isValidFor(String expectedContextFingerprint, Instant now) {
        return contextFingerprint.equals(expectedContextFingerprint)
                && expiresAt.isAfter(now == null ? Instant.now() : now);
    }
}
