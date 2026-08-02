package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;

/**
 * Resolves a requested source scope against server policy and provider
 * availability.
 */
public final class RagSourcePolicyResolver {

    public static final String CONTRACT_VERSION = "rag-source-policy-v1";

    private final RagSourceScope defaultScope;
    private final RagSourceScope maximumScope;
    private final boolean clientSelectionEnabled;
    private final boolean externalProviderAvailable;
    private final String fingerprint;

    public RagSourcePolicyResolver(
            AiWebRagProperties.SourcePolicyProperties properties,
            boolean externalProviderAvailable) {
        Objects.requireNonNull(properties, "properties");
        this.defaultScope = Objects.requireNonNull(properties.getDefaultScope(), "defaultScope");
        this.maximumScope = Objects.requireNonNull(properties.getMaximumScope(), "maximumScope");
        this.clientSelectionEnabled = properties.isClientSelectionEnabled();
        this.externalProviderAvailable = externalProviderAvailable;
        if (defaultScope.isBroaderThan(maximumScope)) {
            throw new IllegalStateException("RAG source-policy defaultScope must not exceed maximumScope");
        }
        this.fingerprint = sha256(String.join("|",
                CONTRACT_VERSION,
                defaultScope.name(),
                maximumScope.name(),
                Boolean.toString(clientSelectionEnabled),
                Boolean.toString(externalProviderAvailable)));
    }

    public static RagSourcePolicyResolver defaults() {
        return new RagSourcePolicyResolver(new AiWebRagProperties.SourcePolicyProperties(), false);
    }

    public ResolvedRagSourcePolicy resolve(String requestedValue) {
        RagSourceScope requested = RagSourceScope.parse(requestedValue);
        RagSourceScope selected = requested;
        ResolvedRagSourcePolicy.Source source = requested == null
                ? ResolvedRagSourcePolicy.Source.SERVER_DEFAULT
                : ResolvedRagSourcePolicy.Source.REQUEST;
        boolean clamped = false;
        ResolvedRagSourcePolicy.ReasonCode reason = ResolvedRagSourcePolicy.ReasonCode.NONE;

        if (requested == null) {
            selected = defaultScope;
        } else if (!clientSelectionEnabled) {
            selected = defaultScope;
            clamped = requested != defaultScope;
            reason = clamped
                    ? ResolvedRagSourcePolicy.ReasonCode.CLIENT_SELECTION_DISABLED
                    : ResolvedRagSourcePolicy.ReasonCode.NONE;
        } else if (requested.isBroaderThan(maximumScope)) {
            selected = maximumScope;
            clamped = true;
            reason = ResolvedRagSourcePolicy.ReasonCode.SERVER_MAXIMUM;
        }

        if (selected == RagSourceScope.DOCUMENT_AND_OFFICIAL_EXTERNAL && !externalProviderAvailable) {
            selected = RagSourceScope.DOCUMENT_ONLY;
            clamped = true;
            reason = ResolvedRagSourcePolicy.ReasonCode.EXTERNAL_PROVIDER_UNAVAILABLE;
        }

        return new ResolvedRagSourcePolicy(
                requested,
                selected,
                source,
                clamped,
                reason,
                CONTRACT_VERSION + ":" + fingerprint.substring(0, 12),
                fingerprint);
    }

    public RagSourceScope defaultScope() {
        return defaultScope;
    }

    public RagSourceScope maximumScope() {
        return maximumScope;
    }

    public boolean clientSelectionEnabled() {
        return clientSelectionEnabled;
    }

    public boolean externalProviderAvailable() {
        return externalProviderAvailable;
    }

    public String policyVersion() {
        return CONTRACT_VERSION + ":" + fingerprint.substring(0, 12);
    }

    public List<String> availableScopes() {
        if (!externalProviderAvailable || maximumScope == RagSourceScope.DOCUMENT_ONLY) {
            return List.of(RagSourceScope.DOCUMENT_ONLY.name());
        }
        return List.of(
                RagSourceScope.DOCUMENT_ONLY.name(),
                RagSourceScope.DOCUMENT_AND_OFFICIAL_EXTERNAL.name());
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
