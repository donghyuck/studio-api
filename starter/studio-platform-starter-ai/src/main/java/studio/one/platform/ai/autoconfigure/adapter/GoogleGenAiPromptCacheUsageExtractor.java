package studio.one.platform.ai.autoconfigure.adapter;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

import studio.one.platform.ai.core.chat.PromptCacheUsage;

final class GoogleGenAiPromptCacheUsageExtractor implements PromptCacheUsageExtractor {

    @Override
    public PromptCacheUsage extract(Usage usage) {
        if (!(usage instanceof GoogleGenAiUsage googleUsage)
                || googleUsage.getCachedContentTokenCount() == null) {
            return null;
        }
        return PromptCacheUsage.complete(
                googleUsage.getPromptTokens(),
                googleUsage.getCachedContentTokenCount(),
                0);
    }
}
