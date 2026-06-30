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
        String blockifyLlmProvider,
        String blockifyLlmModel,
        Boolean blockifyPiiMaskingEnabled,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        boolean useLlmKeywordExtraction,
        String skillExtractionMode,
        boolean generateSkillEmbeddings,
        String skillEmbeddingProvider,
        String skillEmbeddingModel,
        Integer skillEmbeddingDimension) {

    public MarkdownPipelineOptions {
        runRagIndex = runRagIndex || runSkillExtraction;
        runChunking = runChunking || runRagIndex;
        chunkingStrategy = normalizeStrategy(chunkingStrategy);
        chunkUnit = normalizeUnit(chunkUnit);
        blockifyLlmProvider = normalize(blockifyLlmProvider);
        blockifyLlmModel = normalize(blockifyLlmModel);
        embeddingProfileId = normalize(embeddingProfileId);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
        skillEmbeddingProvider = normalize(skillEmbeddingProvider);
        skillEmbeddingModel = normalize(skillEmbeddingModel);
        skillExtractionMode = normalizeSkillExtractionMode(skillExtractionMode);
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
        if (skillEmbeddingDimension != null && skillEmbeddingDimension <= 0) {
            throw new IllegalArgumentException("skillEmbeddingDimension must be greater than zero");
        }
        if (embeddingProfileId != null && (embeddingProvider != null || embeddingModel != null)) {
            throw new IllegalArgumentException(
                    "embeddingProvider/embeddingModel must not be supplied with embeddingProfileId");
        }
        boolean hasBlockifyLlmSelection = blockifyLlmProvider != null || blockifyLlmModel != null
                || blockifyPiiMaskingEnabled != null;
        boolean hasChunkingSelection = chunkingStrategy != null || chunkMaxSize != null
                || chunkOverlap != null || chunkUnit != null || hasBlockifyLlmSelection;
        boolean hasEmbeddingSelection = embeddingProfileId != null || embeddingProvider != null
                || embeddingModel != null || embeddingDimension != null;
        if (!runChunking && hasChunkingSelection) {
            throw new IllegalArgumentException("Chunking options require runChunking");
        }
        if (hasBlockifyLlmSelection && !isBlockifyCompatibleStrategy(chunkingStrategy)) {
            throw new IllegalArgumentException(
                    "Blockify LLM options require chunkingStrategy=blockify or knowledge-block");
        }
        if (!runRagIndex && hasEmbeddingSelection) {
            throw new IllegalArgumentException("Embedding options require runRagIndex");
        }
        if (!runRagIndex && useLlmKeywordExtraction) {
            throw new IllegalArgumentException("LLM keyword extraction requires runRagIndex");
        }
        boolean hasSkillEmbeddingSelection = skillEmbeddingProvider != null
                || skillEmbeddingModel != null
                || skillEmbeddingDimension != null;
        if (!runSkillExtraction && (skillExtractionMode != null
                || generateSkillEmbeddings || hasSkillEmbeddingSelection)) {
            throw new IllegalArgumentException("Skill extraction options require runSkillExtraction");
        }
        if (!generateSkillEmbeddings && hasSkillEmbeddingSelection) {
            throw new IllegalArgumentException(
                    "Skill embedding provider/model/dimension require generateSkillEmbeddings");
        }
    }

    public MarkdownPipelineOptions(
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
        this(runChunking, runRagIndex, runSkillExtraction,
                chunkingStrategy, chunkMaxSize, chunkOverlap, chunkUnit,
                null, null, null,
                embeddingProfileId, embeddingProvider, embeddingModel, embeddingDimension,
                false, null, false, null, null, null);
    }

    public MarkdownPipelineOptions(
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
            Integer embeddingDimension,
            boolean useLlmKeywordExtraction,
            boolean generateSkillEmbeddings,
            String skillEmbeddingProvider,
            String skillEmbeddingModel,
            Integer skillEmbeddingDimension) {
        this(runChunking, runRagIndex, runSkillExtraction,
                chunkingStrategy, chunkMaxSize, chunkOverlap, chunkUnit,
                null, null, null,
                embeddingProfileId, embeddingProvider, embeddingModel, embeddingDimension,
                useLlmKeywordExtraction, null, generateSkillEmbeddings,
                skillEmbeddingProvider, skillEmbeddingModel, skillEmbeddingDimension);
    }

    public MarkdownPipelineOptions(
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
            Integer embeddingDimension,
            boolean useLlmKeywordExtraction,
            String skillExtractionMode,
            boolean generateSkillEmbeddings,
            String skillEmbeddingProvider,
            String skillEmbeddingModel,
            Integer skillEmbeddingDimension) {
        this(runChunking, runRagIndex, runSkillExtraction,
                chunkingStrategy, chunkMaxSize, chunkOverlap, chunkUnit,
                null, null, null,
                embeddingProfileId, embeddingProvider, embeddingModel, embeddingDimension,
                useLlmKeywordExtraction, skillExtractionMode, generateSkillEmbeddings,
                skillEmbeddingProvider, skillEmbeddingModel, skillEmbeddingDimension);
    }

    public MarkdownPipelineOptions(boolean runChunking, boolean runRagIndex, boolean runSkillExtraction) {
        this(runChunking, runRagIndex, runSkillExtraction,
                null, null, null, null, null, null, null, null, null, null, null,
                false, null, false, null, null, null);
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
                && !normalized.equals("structure-based")
                && !normalized.equals("blockify")
                && !normalized.equals("knowledge-block")) {
            throw new IllegalArgumentException("Unsupported chunkingStrategy: " + value);
        }
        return normalized;
    }

    private static boolean isBlockifyCompatibleStrategy(String strategy) {
        return "blockify".equals(strategy) || "knowledge-block".equals(strategy);
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

    private static String normalizeSkillExtractionMode(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!normalized.equals("regex") && !normalized.equals("llm")) {
            throw new IllegalArgumentException("Unsupported skillExtractionMode: " + value);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
