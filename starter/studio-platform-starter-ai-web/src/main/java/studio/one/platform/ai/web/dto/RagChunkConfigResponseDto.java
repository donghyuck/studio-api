package studio.one.platform.ai.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RagChunkConfigResponseDto {
    private final ChunkingConfigDto chunking;
    private final LegacyFallbackConfigDto legacyFallback;
    private final RagContextConfigDto ragContext;
    private final ChunkPreviewLimitsDto limits;

    @JsonCreator
    public RagChunkConfigResponseDto(@JsonProperty("chunking") ChunkingConfigDto chunking,
                                     @JsonProperty("legacyFallback") LegacyFallbackConfigDto legacyFallback,
                                     @JsonProperty("ragContext") RagContextConfigDto ragContext,
                                     @JsonProperty("limits") ChunkPreviewLimitsDto limits) {
        this.chunking = chunking;
        this.legacyFallback = legacyFallback;
        this.ragContext = ragContext;
        this.limits = limits;
    }

    public ChunkingConfigDto chunking() { return chunking; }
    public LegacyFallbackConfigDto legacyFallback() { return legacyFallback; }
    public RagContextConfigDto ragContext() { return ragContext; }
    public ChunkPreviewLimitsDto limits() { return limits; }

    public ChunkingConfigDto getChunking() { return chunking; }
    public LegacyFallbackConfigDto getLegacyFallback() { return legacyFallback; }
    public RagContextConfigDto getRagContext() { return ragContext; }
    public ChunkPreviewLimitsDto getLimits() { return limits; }

    public static class ChunkingConfigDto {
        private final boolean available;
        private final boolean enabled;
        private final String strategy;
        private final String previewStrategy;
        private final boolean defaultStrategyPreviewSupported;
        private final int maxSize;
        private final int overlap;
        private final List<String> availableStrategies;
        private final List<String> registeredChunkers;
        private final boolean chunkingOrchestratorAvailable;

        @JsonCreator
        public ChunkingConfigDto(@JsonProperty("available") boolean available,
                                 @JsonProperty("enabled") boolean enabled,
                                 @JsonProperty("strategy") String strategy,
                                 @JsonProperty("previewStrategy") String previewStrategy,
                                 @JsonProperty("defaultStrategyPreviewSupported") boolean defaultStrategyPreviewSupported,
                                 @JsonProperty("maxSize") int maxSize,
                                 @JsonProperty("overlap") int overlap,
                                 @JsonProperty("availableStrategies") List<String> availableStrategies,
                                 @JsonProperty("registeredChunkers") List<String> registeredChunkers,
                                 @JsonProperty("chunkingOrchestratorAvailable") boolean chunkingOrchestratorAvailable) {
            this.available = available;
            this.enabled = enabled;
            this.strategy = strategy;
            this.previewStrategy = previewStrategy;
            this.defaultStrategyPreviewSupported = defaultStrategyPreviewSupported;
            this.maxSize = maxSize;
            this.overlap = overlap;
            this.availableStrategies = availableStrategies;
            this.registeredChunkers = registeredChunkers;
            this.chunkingOrchestratorAvailable = chunkingOrchestratorAvailable;
        }

        public boolean available() { return available; }
        public boolean enabled() { return enabled; }
        public String strategy() { return strategy; }
        public String previewStrategy() { return previewStrategy; }
        public boolean defaultStrategyPreviewSupported() { return defaultStrategyPreviewSupported; }
        public int maxSize() { return maxSize; }
        public int overlap() { return overlap; }
        public List<String> availableStrategies() { return availableStrategies; }
        public List<String> registeredChunkers() { return registeredChunkers; }
        public boolean chunkingOrchestratorAvailable() { return chunkingOrchestratorAvailable; }

        public boolean isAvailable() { return available; }
        public boolean isEnabled() { return enabled; }
        public String getStrategy() { return strategy; }
        public String getPreviewStrategy() { return previewStrategy; }
        public boolean isDefaultStrategyPreviewSupported() { return defaultStrategyPreviewSupported; }
        public int getMaxSize() { return maxSize; }
        public int getOverlap() { return overlap; }
        public List<String> getAvailableStrategies() { return availableStrategies; }
        public List<String> getRegisteredChunkers() { return registeredChunkers; }
        public boolean isChunkingOrchestratorAvailable() { return chunkingOrchestratorAvailable; }
    }

    public static class LegacyFallbackConfigDto {
        private final int chunkSize;
        private final int chunkOverlap;
        private final boolean textChunkerAvailable;

        @JsonCreator
        public LegacyFallbackConfigDto(@JsonProperty("chunkSize") int chunkSize,
                                       @JsonProperty("chunkOverlap") int chunkOverlap,
                                       @JsonProperty("textChunkerAvailable") boolean textChunkerAvailable) {
            this.chunkSize = chunkSize;
            this.chunkOverlap = chunkOverlap;
            this.textChunkerAvailable = textChunkerAvailable;
        }

        public int chunkSize() { return chunkSize; }
        public int chunkOverlap() { return chunkOverlap; }
        public boolean textChunkerAvailable() { return textChunkerAvailable; }
        public int getChunkSize() { return chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public boolean isTextChunkerAvailable() { return textChunkerAvailable; }
    }

    public static class RagContextConfigDto {
        private final int maxChunks;
        private final int maxChars;
        private final int maxChunkChars;
        private final boolean includeScores;
        private final RagContextExpansionConfigDto expansion;

        @JsonCreator
        public RagContextConfigDto(@JsonProperty("maxChunks") int maxChunks,
                                   @JsonProperty("maxChars") int maxChars,
                                   @JsonProperty("maxChunkChars") int maxChunkChars,
                                   @JsonProperty("includeScores") boolean includeScores,
                                   @JsonProperty("expansion") RagContextExpansionConfigDto expansion) {
            this.maxChunks = maxChunks;
            this.maxChars = maxChars;
            this.maxChunkChars = maxChunkChars;
            this.includeScores = includeScores;
            this.expansion = expansion;
        }

        public int maxChunks() { return maxChunks; }
        public int maxChars() { return maxChars; }
        public int maxChunkChars() { return maxChunkChars; }
        public boolean includeScores() { return includeScores; }
        public RagContextExpansionConfigDto expansion() { return expansion; }
        public int getMaxChunks() { return maxChunks; }
        public int getMaxChars() { return maxChars; }
        public int getMaxChunkChars() { return maxChunkChars; }
        public boolean isIncludeScores() { return includeScores; }
        public RagContextExpansionConfigDto getExpansion() { return expansion; }
    }

    public static class RagContextExpansionConfigDto {
        private final boolean enabled;
        private final int candidateMultiplier;
        private final int maxCandidates;
        private final int previousWindow;
        private final int nextWindow;
        private final boolean includeParentContent;

        @JsonCreator
        public RagContextExpansionConfigDto(@JsonProperty("enabled") boolean enabled,
                                            @JsonProperty("candidateMultiplier") int candidateMultiplier,
                                            @JsonProperty("maxCandidates") int maxCandidates,
                                            @JsonProperty("previousWindow") int previousWindow,
                                            @JsonProperty("nextWindow") int nextWindow,
                                            @JsonProperty("includeParentContent") boolean includeParentContent) {
            this.enabled = enabled;
            this.candidateMultiplier = candidateMultiplier;
            this.maxCandidates = maxCandidates;
            this.previousWindow = previousWindow;
            this.nextWindow = nextWindow;
            this.includeParentContent = includeParentContent;
        }

        public boolean enabled() { return enabled; }
        public int candidateMultiplier() { return candidateMultiplier; }
        public int maxCandidates() { return maxCandidates; }
        public int previousWindow() { return previousWindow; }
        public int nextWindow() { return nextWindow; }
        public boolean includeParentContent() { return includeParentContent; }
        public boolean isEnabled() { return enabled; }
        public int getCandidateMultiplier() { return candidateMultiplier; }
        public int getMaxCandidates() { return maxCandidates; }
        public int getPreviousWindow() { return previousWindow; }
        public int getNextWindow() { return nextWindow; }
        public boolean isIncludeParentContent() { return includeParentContent; }
    }

    public static class ChunkPreviewLimitsDto {
        private final boolean enabled;
        private final int maxInputChars;
        private final int maxPreviewChunks;

        @JsonCreator
        public ChunkPreviewLimitsDto(@JsonProperty("enabled") boolean enabled,
                                     @JsonProperty("maxInputChars") int maxInputChars,
                                     @JsonProperty("maxPreviewChunks") int maxPreviewChunks) {
            this.enabled = enabled;
            this.maxInputChars = maxInputChars;
            this.maxPreviewChunks = maxPreviewChunks;
        }

        public boolean enabled() { return enabled; }
        public int maxInputChars() { return maxInputChars; }
        public int maxPreviewChunks() { return maxPreviewChunks; }
        public boolean isEnabled() { return enabled; }
        public int getMaxInputChars() { return maxInputChars; }
        public int getMaxPreviewChunks() { return maxPreviewChunks; }
    }
}
