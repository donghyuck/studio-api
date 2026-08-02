package studio.one.platform.ai.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable policy snapshot shared by prompt construction, validation, cache,
 * synchronous responses, and SSE completion.
 */
public record ResolvedRagAnswerPolicy(
        RagAnswerMode requestedMode,
        RagAnswerMode effectiveMode,
        Source source,
        boolean clamped,
        ReasonCode reasonCode,
        String policyVersion,
        String fingerprint) {

    public ResolvedRagAnswerPolicy {
        effectiveMode = Objects.requireNonNull(effectiveMode, "effectiveMode");
        source = source == null ? Source.SERVER_DEFAULT : source;
        reasonCode = reasonCode == null ? ReasonCode.NONE : reasonCode;
        policyVersion = policyVersion == null ? "" : policyVersion;
        fingerprint = fingerprint == null ? "" : fingerprint;
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (requestedMode != null) {
            metadata.put("requestedMode", requestedMode.name());
        }
        metadata.put("effectiveMode", effectiveMode.name());
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
        CLIENT_SELECTION_DISABLED
    }
}
