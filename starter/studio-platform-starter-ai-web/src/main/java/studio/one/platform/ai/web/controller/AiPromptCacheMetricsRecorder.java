package studio.one.platform.ai.web.controller;

import studio.one.platform.ai.core.chat.PromptCacheUsage;

public interface AiPromptCacheMetricsRecorder {

    void record(
            String provider,
            String model,
            AiModelUsageRequestKind requestKind,
            PromptCacheUsage promptCacheUsage);

    static AiPromptCacheMetricsRecorder noop() {
        return (provider, model, requestKind, promptCacheUsage) -> {
        };
    }
}
