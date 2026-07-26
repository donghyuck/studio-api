package studio.one.platform.ai.web.controller;

import java.util.Objects;

import io.micrometer.core.instrument.MeterRegistry;
import studio.one.platform.ai.core.chat.PromptCacheUsage;

public final class MicrometerAiPromptCacheMetricsRecorder implements AiPromptCacheMetricsRecorder {

    static final String REQUESTS_METER = "studio.ai.prompt.cache.requests";
    static final String INPUT_TOKENS_METER = "studio.ai.prompt.cache.input.tokens";

    private final MeterRegistry meterRegistry;

    public MicrometerAiPromptCacheMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void record(
            String provider,
            String model,
            AiModelUsageRequestKind requestKind,
            PromptCacheUsage promptCacheUsage) {
        String providerTag = normalize(provider);
        String modelTag = normalize(model);
        String requestKindTag =
                (requestKind == null ? AiModelUsageRequestKind.UNKNOWN : requestKind).name().toLowerCase();
        meterRegistry.counter(
                REQUESTS_METER,
                "provider", providerTag,
                "model", modelTag,
                "requestKind", requestKindTag,
                "outcome", outcome(promptCacheUsage))
                .increment();
        if (promptCacheUsage == null) {
            return;
        }
        incrementTokens(
                providerTag, modelTag, requestKindTag, "read", promptCacheUsage.cacheReadInputTokens());
        incrementTokens(
                providerTag, modelTag, requestKindTag, "write", promptCacheUsage.cacheWriteInputTokens());
    }

    private void incrementTokens(
            String provider,
            String model,
            String requestKind,
            String bucket,
            Integer tokens) {
        if (tokens == null || tokens <= 0) {
            return;
        }
        meterRegistry.counter(
                INPUT_TOKENS_METER,
                "provider", provider,
                "model", model,
                "requestKind", requestKind,
                "bucket", bucket)
                .increment(tokens);
    }

    private String outcome(PromptCacheUsage usage) {
        if (usage == null || !usage.reported()) {
            return "unknown";
        }
        if (usage.cacheHit()) {
            return "hit";
        }
        return usage.completeness() == PromptCacheUsage.Completeness.COMPLETE ? "miss" : "unknown";
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
