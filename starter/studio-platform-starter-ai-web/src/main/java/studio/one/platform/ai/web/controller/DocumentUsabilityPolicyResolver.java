package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.Policy;

/**
 * Immutable policy identity for document-usability decisions.
 */
public final class DocumentUsabilityPolicyResolver {
    public static final String CONTRACT_VERSION = "document-usability-v1";

    private static final String POLICY_INPUT = String.join("|",
            CONTRACT_VERSION,
            "eligibility=blocking-quality-failure",
            "searchability=vector-exists-and-revision-compatible",
            "low-quality=review-not-block",
            "missing-evaluation=review",
            "evaluation=current-revision-and-source-hash",
            "automatic-evaluation=retrieval-only",
            "epub-page=not-applicable");

    private final String fingerprint = sha256(POLICY_INPUT);
    private final String policyVersion = CONTRACT_VERSION + ":" + fingerprint.substring(0, 12);

    public Policy snapshot() {
        return new Policy(policyVersion, fingerprint);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
