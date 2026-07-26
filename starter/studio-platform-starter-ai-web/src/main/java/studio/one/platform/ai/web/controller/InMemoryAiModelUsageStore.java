package studio.one.platform.ai.web.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import studio.one.platform.ai.autoconfigure.AiModelUsageProperties;
import studio.one.platform.ai.core.chat.ChatResponseMetadata;
import studio.one.platform.ai.core.chat.PromptCacheUsage;
import studio.one.platform.ai.core.chat.TokenUsage;

public final class InMemoryAiModelUsageStore implements AiModelUsageStore {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final AiModelUsageProperties properties;
    private final AiPromptCacheMetricsRecorder metricsRecorder;
    private final Map<ModelKey, MutableUsage> usage = new LinkedHashMap<>();

    public InMemoryAiModelUsageStore(AiModelUsageProperties properties) {
        this(properties, AiPromptCacheMetricsRecorder.noop());
    }

    public InMemoryAiModelUsageStore(
            AiModelUsageProperties properties,
            AiPromptCacheMetricsRecorder metricsRecorder) {
        this.properties = properties;
        this.metricsRecorder = metricsRecorder == null ? AiPromptCacheMetricsRecorder.noop() : metricsRecorder;
    }

    @Override
    public synchronized UsageEstimate record(ChatResponseMetadata metadata, String fallbackModel) {
        return record(metadata, fallbackModel, AiModelUsageRequestKind.UNKNOWN);
    }

    @Override
    public synchronized UsageEstimate record(
            ChatResponseMetadata metadata,
            String fallbackModel,
            AiModelUsageRequestKind requestKind) {
        if (!properties.isEnabled() || metadata == null) {
            return UsageEstimate.unavailable();
        }
        String provider = normalize(metadata.provider(), "unknown");
        String model = normalize(metadata.resolvedModel(), normalize(fallbackModel, "unknown"));
        AiModelUsageRequestKind effectiveRequestKind =
                requestKind == null ? AiModelUsageRequestKind.UNKNOWN : requestKind;
        TokenUsage tokens = metadata.tokenUsage();
        long inputTokens = value(tokens.inputTokens());
        long outputTokens = value(tokens.outputTokens());
        long totalTokens = value(tokens.totalTokens());
        boolean tokenUsageAvailable = !tokens.toMap().isEmpty();
        PromptCacheUsage promptCacheUsage = metadata.promptCacheUsage();
        PricingMatch pricing = pricing(provider, model);
        CostEstimate estimate = tokenUsageAvailable && pricing != null
                ? estimate(pricing.pricing(), inputTokens, outputTokens, promptCacheUsage)
                : CostEstimate.unpriced();

        Instant now = Instant.now();
        MutableUsage aggregate = usage.computeIfAbsent(
                new ModelKey(provider, model, effectiveRequestKind),
                ignored -> new MutableUsage(now));
        aggregate.record(
                inputTokens,
                outputTokens,
                totalTokens,
                metadata.latencyMs(),
                promptCacheUsage,
                estimate,
                now);
        metricsRecorder.record(provider, model, effectiveRequestKind, promptCacheUsage);
        return pricing == null || estimate.estimatedCost() == null
                ? new UsageEstimate(currency(), null, false, null)
                : new UsageEstimate(currency(), estimate.estimatedCost(), true, pricing.key());
    }

    @Override
    public synchronized List<ModelUsageSummary> summaries(String provider, String model) {
        String providerFilter = normalize(provider, null);
        String modelFilter = normalize(model, null);
        List<ModelUsageSummary> results = new ArrayList<>();
        usage.forEach((key, value) -> {
            if (matches(key.provider(), providerFilter) && matches(key.model(), modelFilter)) {
                results.add(value.snapshot(key, currency()));
            }
        });
        results.sort(Comparator.comparing(ModelUsageSummary::estimatedCost).reversed()
                .thenComparing(ModelUsageSummary::model));
        return List.copyOf(results);
    }

    private PricingMatch pricing(String provider, String model) {
        String qualifiedKey = provider + "/" + model;
        AiModelUsageProperties.ModelPricing qualified = findPricing(qualifiedKey);
        if (qualified != null) {
            return new PricingMatch(qualifiedKey, qualified);
        }
        AiModelUsageProperties.ModelPricing modelPricing = findPricing(model);
        return modelPricing == null ? null : new PricingMatch(model, modelPricing);
    }

    private AiModelUsageProperties.ModelPricing findPricing(String key) {
        AiModelUsageProperties.ModelPricing direct = properties.getPricing().get(key);
        if (direct != null) {
            return direct;
        }
        return properties.getPricing().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private CostEstimate estimate(
            AiModelUsageProperties.ModelPricing pricing,
            long inputTokens,
            long outputTokens,
            PromptCacheUsage promptCacheUsage) {
        boolean highContext = pricing.getHighContextThresholdTokens() != null
                && inputTokens > pricing.getHighContextThresholdTokens();
        BigDecimal inputRate = highContext && pricing.getHighContextInputPerMillionTokens() != null
                ? pricing.getHighContextInputPerMillionTokens()
                : pricing.getInputPerMillionTokens();
        BigDecimal outputRate = highContext && pricing.getHighContextOutputPerMillionTokens() != null
                ? pricing.getHighContextOutputPerMillionTokens()
                : pricing.getOutputPerMillionTokens();
        if (inputRate == null || outputRate == null) {
            return CostEstimate.unpriced();
        }
        BigDecimal baselineInputCost = tokenCost(inputTokens, inputRate);
        BigDecimal outputCost = tokenCost(outputTokens, outputRate);
        if (promptCacheUsage == null) {
            return new CostEstimate(
                    baselineInputCost.add(outputCost).setScale(12, RoundingMode.HALF_UP),
                    baselineInputCost,
                    BigDecimal.ZERO.setScale(12, RoundingMode.HALF_UP));
        }
        PromptCacheUsage validated = promptCacheUsage.validatedAgainst((int) Math.min(Integer.MAX_VALUE, inputTokens));
        if (validated.completeness() != PromptCacheUsage.Completeness.COMPLETE
                || validated.uncachedInputTokens() == null
                || validated.cacheReadInputTokens() == null
                || validated.cacheWriteInputTokens() == null) {
            return new CostEstimate(null, baselineInputCost, null);
        }
        BigDecimal cacheReadRate = highContext && pricing.getHighContextCacheReadInputPerMillionTokens() != null
                ? pricing.getHighContextCacheReadInputPerMillionTokens()
                : pricing.getCacheReadInputPerMillionTokens();
        BigDecimal cacheWriteRate = highContext && pricing.getHighContextCacheWriteInputPerMillionTokens() != null
                ? pricing.getHighContextCacheWriteInputPerMillionTokens()
                : pricing.getCacheWriteInputPerMillionTokens();
        if ((validated.cacheReadInputTokens() > 0 && cacheReadRate == null)
                || (validated.cacheWriteInputTokens() > 0 && cacheWriteRate == null)) {
            return new CostEstimate(null, baselineInputCost, null);
        }
        BigDecimal actualInputCost = tokenCost(validated.uncachedInputTokens(), inputRate)
                .add(tokenCost(validated.cacheReadInputTokens(), zeroIfNull(cacheReadRate)))
                .add(tokenCost(validated.cacheWriteInputTokens(), zeroIfNull(cacheWriteRate)));
        BigDecimal estimatedCost = actualInputCost.add(outputCost).setScale(12, RoundingMode.HALF_UP);
        BigDecimal savings = baselineInputCost.subtract(actualInputCost).setScale(12, RoundingMode.HALF_UP);
        return new CostEstimate(estimatedCost, baselineInputCost, savings);
    }

    private BigDecimal tokenCost(long tokens, BigDecimal rate) {
        return BigDecimal.valueOf(tokens).multiply(rate).divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String currency() {
        return normalize(properties.getCurrency(), "USD").toUpperCase(Locale.ROOT);
    }

    private boolean matches(String value, String filter) {
        return filter == null || value.equalsIgnoreCase(filter);
    }

    private static long value(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record ModelKey(String provider, String model, AiModelUsageRequestKind requestKind) {
    }

    private record PricingMatch(String key, AiModelUsageProperties.ModelPricing pricing) {
    }

    private record CostEstimate(
            BigDecimal estimatedCost,
            BigDecimal estimatedBaselineInputCost,
            BigDecimal estimatedInputSavings) {

        private static CostEstimate unpriced() {
            return new CostEstimate(null, null, null);
        }
    }

    private static final class MutableUsage {
        private long requestCount;
        private long pricedRequestCount;
        private long cacheReportedRequestCount;
        private long cacheHitRequestCount;
        private long inputTokens;
        private long uncachedInputTokens;
        private long cacheReadInputTokens;
        private long cacheWriteInputTokens;
        private long outputTokens;
        private long totalTokens;
        private long totalLatencyMs;
        private BigDecimal estimatedCost = BigDecimal.ZERO;
        private BigDecimal estimatedBaselineInputCost = BigDecimal.ZERO;
        private BigDecimal estimatedInputSavings = BigDecimal.ZERO;
        private final Instant firstSeenAt;
        private Instant lastSeenAt;

        private MutableUsage(Instant firstSeenAt) {
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = firstSeenAt;
        }

        private void record(
                long input,
                long output,
                long total,
                Long latencyMs,
                PromptCacheUsage promptCacheUsage,
                CostEstimate cost,
                Instant now) {
            requestCount++;
            inputTokens += input;
            outputTokens += output;
            totalTokens += total;
            totalLatencyMs += latencyMs == null ? 0L : Math.max(0L, latencyMs);
            if (promptCacheUsage != null && promptCacheUsage.reported()) {
                cacheReportedRequestCount++;
                if (promptCacheUsage.cacheHit()) {
                    cacheHitRequestCount++;
                }
                uncachedInputTokens += value(promptCacheUsage.uncachedInputTokens());
                cacheReadInputTokens += value(promptCacheUsage.cacheReadInputTokens());
                cacheWriteInputTokens += value(promptCacheUsage.cacheWriteInputTokens());
            } else {
                uncachedInputTokens += input;
            }
            if (cost.estimatedCost() != null) {
                pricedRequestCount++;
                estimatedCost = estimatedCost.add(cost.estimatedCost());
                estimatedBaselineInputCost =
                        estimatedBaselineInputCost.add(cost.estimatedBaselineInputCost());
                estimatedInputSavings = estimatedInputSavings.add(cost.estimatedInputSavings());
            }
            lastSeenAt = now;
        }

        private ModelUsageSummary snapshot(ModelKey key, String currency) {
            double averageLatency = requestCount == 0 ? 0.0d : (double) totalLatencyMs / requestCount;
            Double cacheHitRate = cacheReportedRequestCount == 0
                    ? null
                    : (double) cacheHitRequestCount / cacheReportedRequestCount;
            return new ModelUsageSummary(
                    key.provider(), key.model(), key.requestKind(), requestCount, pricedRequestCount,
                    cacheReportedRequestCount, cacheHitRequestCount, cacheHitRate,
                    inputTokens, uncachedInputTokens, cacheReadInputTokens, cacheWriteInputTokens,
                    outputTokens, totalTokens, totalLatencyMs, averageLatency,
                    currency, estimatedCost.setScale(12, RoundingMode.HALF_UP),
                    estimatedBaselineInputCost.setScale(12, RoundingMode.HALF_UP),
                    estimatedInputSavings.setScale(12, RoundingMode.HALF_UP),
                    firstSeenAt, lastSeenAt);
        }
    }
}
