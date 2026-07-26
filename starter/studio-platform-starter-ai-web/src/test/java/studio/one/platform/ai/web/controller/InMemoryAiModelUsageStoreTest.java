package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.autoconfigure.AiModelUsageProperties;
import studio.one.platform.ai.core.chat.ChatResponseMetadata;
import studio.one.platform.ai.core.chat.PromptCacheUsage;

class InMemoryAiModelUsageStoreTest {

    @Test
    void aggregatesTokenUsageAndEstimatesConfiguredModelCost() {
        AiModelUsageProperties properties = new AiModelUsageProperties();
        AiModelUsageProperties.ModelPricing pricing = new AiModelUsageProperties.ModelPricing();
        pricing.setInputPerMillionTokens(new BigDecimal("1.25"));
        pricing.setOutputPerMillionTokens(new BigDecimal("10.00"));
        properties.getPricing().put("gemini-2.5-pro", pricing);
        InMemoryAiModelUsageStore store = new InMemoryAiModelUsageStore(properties);

        AiModelUsageStore.UsageEstimate estimate = store.record(ChatResponseMetadata.from(Map.of(
                "provider", "GOOGLE_AI_GEMINI",
                "resolvedModel", "gemini-2.5-pro",
                "latencyMs", 1500L,
                "tokenUsage", Map.of(
                        "inputTokens", 1000,
                        "outputTokens", 500,
                        "totalTokens", 1500))), null);

        assertThat(estimate.pricingConfigured()).isTrue();
        assertThat(estimate.estimatedCost()).isEqualByComparingTo("0.006250000000");
        assertThat(store.summaries(null, null)).singleElement().satisfies(summary -> {
            assertThat(summary.model()).isEqualTo("gemini-2.5-pro");
            assertThat(summary.requestCount()).isEqualTo(1);
            assertThat(summary.inputTokens()).isEqualTo(1000);
            assertThat(summary.outputTokens()).isEqualTo(500);
            assertThat(summary.estimatedCost()).isEqualByComparingTo("0.006250000000");
        });
    }

    @Test
    void appliesHighContextPricingAndKeepsUnpricedRequestsVisible() {
        AiModelUsageProperties properties = new AiModelUsageProperties();
        AiModelUsageProperties.ModelPricing pricing = new AiModelUsageProperties.ModelPricing();
        pricing.setInputPerMillionTokens(new BigDecimal("1.25"));
        pricing.setOutputPerMillionTokens(new BigDecimal("10.00"));
        pricing.setHighContextThresholdTokens(200_000);
        pricing.setHighContextInputPerMillionTokens(new BigDecimal("2.50"));
        pricing.setHighContextOutputPerMillionTokens(new BigDecimal("15.00"));
        properties.getPricing().put("gemini-2.5-pro", pricing);
        InMemoryAiModelUsageStore store = new InMemoryAiModelUsageStore(properties);

        store.record(ChatResponseMetadata.from(Map.of(
                "provider", "GOOGLE_AI_GEMINI",
                "resolvedModel", "gemini-2.5-pro",
                "tokenUsage", Map.of("inputTokens", 300_000, "outputTokens", 10_000, "totalTokens", 310_000))), null);
        AiModelUsageStore.UsageEstimate unpriced = store.record(ChatResponseMetadata.from(Map.of(
                "provider", "LOCAL",
                "resolvedModel", "local-model")), null);

        assertThat(store.summaries(null, "gemini-2.5-pro").get(0).estimatedCost())
                .isEqualByComparingTo("0.900000000000");
        assertThat(unpriced.pricingConfigured()).isFalse();
        assertThat(store.summaries("LOCAL", null).get(0).pricedRequestCount()).isZero();
    }

    @Test
    void pricesCompletePromptCacheBucketsAndGroupsRequestKinds() {
        AiModelUsageProperties properties = new AiModelUsageProperties();
        AiModelUsageProperties.ModelPricing pricing = new AiModelUsageProperties.ModelPricing();
        pricing.setInputPerMillionTokens(new BigDecimal("1.25"));
        pricing.setOutputPerMillionTokens(new BigDecimal("10.00"));
        pricing.setCacheReadInputPerMillionTokens(new BigDecimal("0.125"));
        pricing.setCacheWriteInputPerMillionTokens(new BigDecimal("1.5625"));
        properties.getPricing().put("gemini-2.5-pro", pricing);
        InMemoryAiModelUsageStore store = new InMemoryAiModelUsageStore(properties);

        AiModelUsageStore.UsageEstimate estimate = store.record(
                ChatResponseMetadata.from(Map.of(
                        "provider", "GOOGLE_AI_GEMINI",
                        "resolvedModel", "gemini-2.5-pro",
                        "tokenUsage", Map.of(
                                "inputTokens", 1000,
                                "outputTokens", 500,
                                "totalTokens", 1500),
                        "promptCacheUsage", Map.of(
                                PromptCacheUsage.KEY_UNCACHED_INPUT_TOKENS, 600,
                                PromptCacheUsage.KEY_CACHE_READ_INPUT_TOKENS, 300,
                                PromptCacheUsage.KEY_CACHE_WRITE_INPUT_TOKENS, 100,
                                PromptCacheUsage.KEY_COMPLETENESS, "COMPLETE"))),
                null,
                AiModelUsageRequestKind.RAG);

        assertThat(estimate.pricingConfigured()).isTrue();
        assertThat(estimate.estimatedCost()).isEqualByComparingTo("0.005943750000");
        assertThat(store.summaries(null, null)).singleElement().satisfies(summary -> {
            assertThat(summary.requestKind()).isEqualTo(AiModelUsageRequestKind.RAG);
            assertThat(summary.cacheReportedRequestCount()).isEqualTo(1);
            assertThat(summary.cacheHitRequestCount()).isEqualTo(1);
            assertThat(summary.cacheHitRate()).isEqualTo(1.0d);
            assertThat(summary.uncachedInputTokens()).isEqualTo(600);
            assertThat(summary.cacheReadInputTokens()).isEqualTo(300);
            assertThat(summary.cacheWriteInputTokens()).isEqualTo(100);
            assertThat(summary.estimatedBaselineInputCost()).isEqualByComparingTo("0.001250000000");
            assertThat(summary.estimatedInputSavings()).isEqualByComparingTo("0.000306250000");
        });
    }

    @Test
    void keepsPartialPromptCacheUsageVisibleButDoesNotGuessCost() {
        AiModelUsageProperties properties = new AiModelUsageProperties();
        AiModelUsageProperties.ModelPricing pricing = new AiModelUsageProperties.ModelPricing();
        pricing.setInputPerMillionTokens(BigDecimal.ONE);
        pricing.setOutputPerMillionTokens(BigDecimal.ONE);
        pricing.setCacheReadInputPerMillionTokens(new BigDecimal("0.1"));
        properties.getPricing().put("model", pricing);
        InMemoryAiModelUsageStore store = new InMemoryAiModelUsageStore(properties);

        AiModelUsageStore.UsageEstimate estimate = store.record(
                ChatResponseMetadata.from(Map.of(
                        "resolvedModel", "model",
                        "tokenUsage", Map.of("inputTokens", 100, "outputTokens", 10, "totalTokens", 110),
                        "promptCacheUsage", Map.of(
                                PromptCacheUsage.KEY_CACHE_READ_INPUT_TOKENS, 50,
                                PromptCacheUsage.KEY_COMPLETENESS, "PARTIAL"))),
                null,
                AiModelUsageRequestKind.CHAT);

        assertThat(estimate.pricingConfigured()).isFalse();
        assertThat(store.summaries(null, null)).singleElement().satisfies(summary -> {
            assertThat(summary.pricedRequestCount()).isZero();
            assertThat(summary.cacheReportedRequestCount()).isEqualTo(1);
            assertThat(summary.cacheReadInputTokens()).isEqualTo(50);
        });
    }
}
