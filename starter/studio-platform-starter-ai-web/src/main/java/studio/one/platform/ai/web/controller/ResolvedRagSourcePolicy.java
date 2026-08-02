package studio.one.platform.ai.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable source-policy snapshot shared by retrieval, prompt construction,
 * validation, cache, synchronous responses, and SSE completion.
 */
public record ResolvedRagSourcePolicy(
        RagSourceScope requestedScope,
        RagSourceScope effectiveScope,
        Source source,
        boolean clamped,
        ReasonCode reasonCode,
        String policyVersion,
        String fingerprint) {

    public ResolvedRagSourcePolicy {
        effectiveScope = Objects.requireNonNull(effectiveScope, "effectiveScope");
        source = source == null ? Source.SERVER_DEFAULT : source;
        reasonCode = reasonCode == null ? ReasonCode.NONE : reasonCode;
        policyVersion = policyVersion == null ? "" : policyVersion;
        fingerprint = fingerprint == null ? "" : fingerprint;
    }

    public boolean externalSourcesEnabled() {
        return effectiveScope == RagSourceScope.DOCUMENT_AND_OFFICIAL_EXTERNAL;
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (requestedScope != null) {
            metadata.put("requestedScope", requestedScope.name());
        }
        metadata.put("effectiveScope", effectiveScope.name());
        metadata.put("source", source.name());
        metadata.put("clamped", clamped);
        metadata.put("reasonCode", reasonCode.name());
        metadata.put("policyVersion", policyVersion);
        return Map.copyOf(metadata);
    }

    public enum Source {
        REQUEST,
        SERVER_DEFAULT
    }

    public enum ReasonCode {
        NONE,
        SERVER_MAXIMUM,
        CLIENT_SELECTION_DISABLED,
        EXTERNAL_PROVIDER_UNAVAILABLE
    }
}
