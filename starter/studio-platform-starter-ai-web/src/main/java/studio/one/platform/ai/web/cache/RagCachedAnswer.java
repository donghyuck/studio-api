package studio.one.platform.ai.web.cache;

import java.time.Instant;

public record RagCachedAnswer(
        String canonicalContent,
        String model,
        String citationValidationStatus,
        String contextFingerprint,
        String effectiveMode,
        String policyFingerprint,
        String policyValidationStatus,
        boolean partial,
        int originalValidationUnitCount,
        int omittedValidationUnitCount,
        Instant createdAt,
        Instant expiresAt) {

    public RagCachedAnswer {
        if (canonicalContent == null || canonicalContent.isBlank()) {
            throw new IllegalArgumentException("canonicalContent must not be blank");
        }
        model = model == null ? "" : model;
        citationValidationStatus = citationValidationStatus == null ? "" : citationValidationStatus;
        contextFingerprint = contextFingerprint == null ? "" : contextFingerprint;
        effectiveMode = effectiveMode == null ? "" : effectiveMode;
        policyFingerprint = policyFingerprint == null ? "" : policyFingerprint;
        policyValidationStatus = policyValidationStatus == null ? "" : policyValidationStatus;
        originalValidationUnitCount = Math.max(0, originalValidationUnitCount);
        omittedValidationUnitCount = Math.max(0, omittedValidationUnitCount);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        expiresAt = expiresAt == null ? createdAt : expiresAt;
    }

    public RagCachedAnswer(
            String canonicalContent,
            String model,
            String citationValidationStatus,
            String contextFingerprint,
            String effectiveMode,
            String policyFingerprint,
            String policyValidationStatus,
            Instant createdAt,
            Instant expiresAt) {
        this(
                canonicalContent,
                model,
                citationValidationStatus,
                contextFingerprint,
                effectiveMode,
                policyFingerprint,
                policyValidationStatus,
                false,
                0,
                0,
                createdAt,
                expiresAt);
    }

    public RagCachedAnswer(
            String canonicalContent,
            String model,
            String citationValidationStatus,
            String contextFingerprint,
            Instant createdAt,
            Instant expiresAt) {
        this(
                canonicalContent,
                model,
                citationValidationStatus,
                contextFingerprint,
                "",
                "",
                "",
                false,
                0,
                0,
                createdAt,
                expiresAt);
    }

    public boolean isValidFor(String expectedContextFingerprint, Instant now) {
        return contextFingerprint.equals(expectedContextFingerprint)
                && expiresAt.isAfter(now == null ? Instant.now() : now);
    }

    public boolean isValidFor(
            String expectedContextFingerprint,
            String expectedPolicyFingerprint,
            Instant now) {
        return isValidFor(expectedContextFingerprint, now)
                && policyFingerprint.equals(expectedPolicyFingerprint == null ? "" : expectedPolicyFingerprint);
    }
}
