package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;

/**
 * Resolves a client request inside the server's answer-policy ceiling.
 */
public final class RagAnswerPolicyResolver {

    public static final String CONTRACT_VERSION = "rag-answer-policy-v2";

    private final RagAnswerMode defaultMode;
    private final RagAnswerMode maximumMode;
    private final boolean clientSelectionEnabled;
    private final String policyVersion;
    private final String fingerprint;

    public RagAnswerPolicyResolver(AiWebRagProperties.AnswerPolicyProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.defaultMode = Objects.requireNonNull(properties.getDefaultMode(), "defaultMode");
        this.maximumMode = Objects.requireNonNull(properties.getMaximumMode(), "maximumMode");
        this.clientSelectionEnabled = properties.isClientSelectionEnabled();
        if (defaultMode.isMorePermissiveThan(maximumMode)) {
            throw new IllegalStateException(
                    "studio.ai.endpoints.rag.answer-policy.default-mode must not exceed maximum-mode");
        }
        this.fingerprint = sha256(defaultMode.name()
                + "|" + maximumMode.name()
                + "|" + clientSelectionEnabled
                + "|" + properties.isFactualListPartialAnswerEnabled());
        this.policyVersion = CONTRACT_VERSION + ":" + fingerprint.substring(0, 12);
    }

    public static RagAnswerPolicyResolver defaults() {
        return new RagAnswerPolicyResolver(new AiWebRagProperties.AnswerPolicyProperties());
    }

    public ResolvedRagAnswerPolicy resolve(String requestedValue) {
        RagAnswerMode requested = RagAnswerMode.parse(requestedValue);
        if (!clientSelectionEnabled) {
            boolean clamped = requested != null && requested != defaultMode;
            return resolved(
                    requested,
                    defaultMode,
                    ResolvedRagAnswerPolicy.Source.SERVER_DEFAULT,
                    clamped,
                    clamped
                            ? ResolvedRagAnswerPolicy.ReasonCode.CLIENT_SELECTION_DISABLED
                            : ResolvedRagAnswerPolicy.ReasonCode.NONE);
        }
        if (requested == null) {
            return resolved(
                    null,
                    defaultMode,
                    ResolvedRagAnswerPolicy.Source.SERVER_DEFAULT,
                    false,
                    ResolvedRagAnswerPolicy.ReasonCode.NONE);
        }
        if (requested.isMorePermissiveThan(maximumMode)) {
            return resolved(
                    requested,
                    maximumMode,
                    ResolvedRagAnswerPolicy.Source.REQUEST,
                    true,
                    ResolvedRagAnswerPolicy.ReasonCode.SERVER_MAXIMUM);
        }
        return resolved(
                requested,
                requested,
                ResolvedRagAnswerPolicy.Source.REQUEST,
                false,
                ResolvedRagAnswerPolicy.ReasonCode.NONE);
    }

    public RagAnswerMode defaultMode() {
        return defaultMode;
    }

    public RagAnswerMode maximumMode() {
        return maximumMode;
    }

    public boolean clientSelectionEnabled() {
        return clientSelectionEnabled;
    }

    public String policyVersion() {
        return policyVersion;
    }

    private ResolvedRagAnswerPolicy resolved(
            RagAnswerMode requested,
            RagAnswerMode effective,
            ResolvedRagAnswerPolicy.Source source,
            boolean clamped,
            ResolvedRagAnswerPolicy.ReasonCode reasonCode) {
        return new ResolvedRagAnswerPolicy(
                requested,
                effective,
                source,
                clamped,
                reasonCode,
                policyVersion,
                fingerprint);
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
