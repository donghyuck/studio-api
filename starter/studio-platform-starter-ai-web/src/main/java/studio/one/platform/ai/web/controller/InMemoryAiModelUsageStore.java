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
import studio.one.platform.ai.core.chat.TokenUsage;

public final class InMemoryAiModelUsageStore implements AiModelUsageStore {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final AiModelUsageProperties properties;
    private final Map<ModelKey, MutableUsage> usage = new LinkedHashMap<>();

    public InMemoryAiModelUsageStore(AiModelUsageProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized UsageEstimate record(ChatResponseMetadata metadata, String fallbackModel) {
        if (!properties.isEnabled() || metadata == null) {
            return UsageEstimate.unavailable();
        }
        String provider = normalize(metadata.provider(), "unknown");
        String model = normalize(metadata.resolvedModel(), normalize(fallbackModel, "unknown"));
        TokenUsage tokens = metadata.tokenUsage();
        long inputTokens = value(tokens.inputTokens());
        long outputTokens = value(tokens.outputTokens());
        long totalTokens = value(tokens.totalTokens());
        boolean tokenUsageAvailable = !tokens.toMap().isEmpty();
        PricingMatch pricing = pricing(provider, model);
        BigDecimal cost = tokenUsageAvailable && pricing != null
                ? estimate(pricing.pricing(), inputTokens, outputTokens)
                : null;

        Instant now = Instant.now();
        MutableUsage aggregate = usage.computeIfAbsent(new ModelKey(provider, model), ignored -> new MutableUsage(now));
        aggregate.record(inputTokens, outputTokens, totalTokens, metadata.latencyMs(), cost, now);
        return pricing == null || cost == null
                ? new UsageEstimate(currency(), null, false, null)
                : new UsageEstimate(currency(), cost, true, pricing.key());
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

    private BigDecimal estimate(AiModelUsageProperties.ModelPricing pricing, long inputTokens, long outputTokens) {
        boolean highContext = pricing.getHighContextThresholdTokens() != null
                && inputTokens > pricing.getHighContextThresholdTokens();
        BigDecimal inputRate = highContext && pricing.getHighContextInputPerMillionTokens() != null
                ? pricing.getHighContextInputPerMillionTokens()
                : pricing.getInputPerMillionTokens();
        BigDecimal outputRate = highContext && pricing.getHighContextOutputPerMillionTokens() != null
                ? pricing.getHighContextOutputPerMillionTokens()
                : pricing.getOutputPerMillionTokens();
        if (inputRate == null || outputRate == null) {
            return null;
        }
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens).multiply(inputRate).divide(ONE_MILLION, 12,
                RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens).multiply(outputRate).divide(ONE_MILLION, 12,
                RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(12, RoundingMode.HALF_UP);
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

    private record ModelKey(String provider, String model) {
    }

    private record PricingMatch(String key, AiModelUsageProperties.ModelPricing pricing) {
    }

    private static final class MutableUsage {
        private long requestCount;
        private long pricedRequestCount;
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private long totalLatencyMs;
        private BigDecimal estimatedCost = BigDecimal.ZERO;
        private final Instant firstSeenAt;
        private Instant lastSeenAt;

        private MutableUsage(Instant firstSeenAt) {
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = firstSeenAt;
        }

        private void record(long input, long output, long total, Long latencyMs, BigDecimal cost, Instant now) {
            requestCount++;
            inputTokens += input;
            outputTokens += output;
            totalTokens += total;
            totalLatencyMs += latencyMs == null ? 0L : Math.max(0L, latencyMs);
            if (cost != null) {
                pricedRequestCount++;
                estimatedCost = estimatedCost.add(cost);
            }
            lastSeenAt = now;
        }

        private ModelUsageSummary snapshot(ModelKey key, String currency) {
            double averageLatency = requestCount == 0 ? 0.0d : (double) totalLatencyMs / requestCount;
            return new ModelUsageSummary(
                    key.provider(), key.model(), requestCount, pricedRequestCount,
                    inputTokens, outputTokens, totalTokens, totalLatencyMs, averageLatency,
                    currency, estimatedCost.setScale(12, RoundingMode.HALF_UP), firstSeenAt, lastSeenAt);
        }
    }
}
