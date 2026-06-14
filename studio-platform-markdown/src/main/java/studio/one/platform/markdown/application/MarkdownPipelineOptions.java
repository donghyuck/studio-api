package studio.one.platform.markdown.application;

import java.util.Locale;

public record MarkdownPipelineOptions(
        boolean runChunking,
        boolean runRagIndex,
        boolean runSkillExtraction,
        String chunkingStrategy,
        Integer chunkMaxSize,
        Integer chunkOverlap,
        String chunkUnit,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension) {

    public MarkdownPipelineOptions {
        runRagIndex = runRagIndex || runSkillExtraction;
        runChunking = runChunking || runRagIndex;
        chunkingStrategy = normalizeStrategy(chunkingStrategy);
        chunkUnit = normalizeUnit(chunkUnit);
        embeddingProfileId = normalize(embeddingProfileId);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
        if (chunkMaxSize != null && chunkMaxSize <= 0) {
            throw new IllegalArgumentException("chunkMaxSize must be greater than zero");
        }
        if (chunkOverlap != null && chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap must not be negative");
        }
        if (chunkMaxSize != null && chunkOverlap != null && chunkOverlap >= chunkMaxSize) {
            throw new IllegalArgumentException("chunkOverlap must be less than chunkMaxSize");
        }
        if (embeddingDimension != null && embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be greater than zero");
        }
        if (embeddingProfileId != null && (embeddingProvider != null || embeddingModel != null)) {
            throw new IllegalArgumentException(
                    "embeddingProvider/embeddingModel must not be supplied with embeddingProfileId");
        }
        boolean hasChunkingSelection = chunkingStrategy != null || chunkMaxSize != null
                || chunkOverlap != null || chunkUnit != null;
        boolean hasEmbeddingSelection = embeddingProfileId != null || embeddingProvider != null
                || embeddingModel != null || embeddingDimension != null;
        if (!runChunking && hasChunkingSelection) {
            throw new IllegalArgumentException("Chunking options require runChunking");
        }
        if (!runRagIndex && hasEmbeddingSelection) {
            throw new IllegalArgumentException("Embedding options require runRagIndex");
        }
    }

    public MarkdownPipelineOptions(boolean runChunking, boolean runRagIndex, boolean runSkillExtraction) {
        this(runChunking, runRagIndex, runSkillExtraction,
                null, null, null, null, null, null, null, null);
    }

    public static MarkdownPipelineOptions none() {
        return new MarkdownPipelineOptions(false, false, false);
    }

    private static String normalizeStrategy(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT).replace('_', '-');
        if (!normalized.equals("fixed-size")
                && !normalized.equals("recursive")
                && !normalized.equals("structure-based")) {
            throw new IllegalArgumentException("Unsupported chunkingStrategy: " + value);
        }
        return normalized;
    }

    private static String normalizeUnit(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.equals("CHARACTER") && !normalized.equals("TOKEN")) {
            throw new IllegalArgumentException("Unsupported chunkUnit: " + value);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
