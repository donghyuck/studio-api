package studio.one.platform.markdown.web;

import jakarta.validation.constraints.Positive;

public record MarkdownDocumentRequest(
        @Positive long attachmentId,
        boolean runChunking,
        boolean runRagIndex,
        boolean runSkillExtraction,
        boolean force,
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
}
