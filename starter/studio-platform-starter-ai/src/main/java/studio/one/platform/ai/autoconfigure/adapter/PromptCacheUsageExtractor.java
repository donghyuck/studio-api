package studio.one.platform.ai.autoconfigure.adapter;

import org.springframework.ai.chat.metadata.Usage;

import studio.one.platform.ai.core.chat.PromptCacheUsage;

@FunctionalInterface
interface PromptCacheUsageExtractor {

    PromptCacheUsage extract(Usage usage);

    static PromptCacheUsageExtractor none() {
        return usage -> null;
    }
}
