package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import studio.one.platform.ai.core.chat.PromptCacheUsage;

class MicrometerAiPromptCacheMetricsRecorderTest {

    @Test
    void recordsOnlyLowCardinalityPromptCacheMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAiPromptCacheMetricsRecorder recorder = new MicrometerAiPromptCacheMetricsRecorder(registry);

        recorder.record(
                "GOOGLE_AI_GEMINI",
                "gemini-2.5-pro",
                AiModelUsageRequestKind.RAG,
                PromptCacheUsage.complete(100, 40, 0));

        assertThat(registry.get(MicrometerAiPromptCacheMetricsRecorder.REQUESTS_METER)
                .tags(
                        "provider", "GOOGLE_AI_GEMINI",
                        "model", "gemini-2.5-pro",
                        "requestKind", "rag",
                        "outcome", "hit")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get(MicrometerAiPromptCacheMetricsRecorder.INPUT_TOKENS_METER)
                .tags(
                        "provider", "GOOGLE_AI_GEMINI",
                        "model", "gemini-2.5-pro",
                        "requestKind", "rag",
                        "bucket", "read")
                .counter()
                .count()).isEqualTo(40.0d);
    }
}
