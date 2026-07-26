package studio.one.platform.ai.web.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.chat.ChatResponseMetadata;

public interface AiModelUsageStore {

    UsageEstimate record(ChatResponseMetadata metadata, String fallbackModel);

    default UsageEstimate record(
            ChatResponseMetadata metadata,
            String fallbackModel,
            AiModelUsageRequestKind requestKind) {
        return record(metadata, fallbackModel);
    }

    List<ModelUsageSummary> summaries(String provider, String model);

    static AiModelUsageStore noop() {
        return new AiModelUsageStore() {
            @Override
            public UsageEstimate record(ChatResponseMetadata metadata, String fallbackModel) {
                return UsageEstimate.unavailable();
            }

            @Override
            public List<ModelUsageSummary> summaries(String provider, String model) {
                return List.of();
            }
        };
    }

    record UsageEstimate(
            String currency,
            BigDecimal estimatedCost,
            boolean pricingConfigured,
            String pricingKey) {

        static UsageEstimate unavailable() {
            return new UsageEstimate("USD", null, false, null);
        }

        public Map<String, Object> toMetadata() {
            if (!pricingConfigured || estimatedCost == null) {
                return Map.of("pricingConfigured", false);
            }
            return Map.of(
                    "currency", currency,
                    "estimatedCost", estimatedCost,
                    "pricingConfigured", true,
                    "pricingKey", pricingKey);
        }
    }

    record ModelUsageSummary(
            String provider,
            String model,
            AiModelUsageRequestKind requestKind,
            long requestCount,
            long pricedRequestCount,
            long cacheReportedRequestCount,
            long cacheHitRequestCount,
            Double cacheHitRate,
            long inputTokens,
            long uncachedInputTokens,
            long cacheReadInputTokens,
            long cacheWriteInputTokens,
            long outputTokens,
            long totalTokens,
            long totalLatencyMs,
            double averageLatencyMs,
            String currency,
            BigDecimal estimatedCost,
            BigDecimal estimatedBaselineInputCost,
            BigDecimal estimatedInputSavings,
            Instant firstSeenAt,
            Instant lastSeenAt) {
    }
}
