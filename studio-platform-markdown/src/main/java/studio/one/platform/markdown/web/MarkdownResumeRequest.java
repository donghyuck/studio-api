package studio.one.platform.markdown.web;

import studio.one.platform.markdown.domain.MarkdownPipelineStage;

public record MarkdownResumeRequest(
        MarkdownPipelineStage fromStage,
        Boolean runChunking,
        Boolean runRagIndex,
        Boolean runSkillExtraction,
        String chunkingStrategy,
        Integer chunkMaxSize,
        Integer chunkOverlap,
        String chunkUnit,
        String blockifyLlmProvider,
        String blockifyLlmModel,
        Boolean blockifyPiiMaskingEnabled,
        String embeddingDeploymentId,
        String embeddingModelId,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        Boolean useLlmKeywordExtraction,
        String skillExtractionMode,
        Boolean generateSkillEmbeddings,
        String skillEmbeddingProvider,
        String skillEmbeddingModel,
        Integer skillEmbeddingDimension,
        Boolean ocrRequired,
        String ocrLanguage,
        String ocrMode,
        Boolean mathVisionCorrection
) {
    public MarkdownResumeRequest(
            MarkdownPipelineStage fromStage, Boolean runChunking, Boolean runRagIndex,
            Boolean runSkillExtraction, String chunkingStrategy, Integer chunkMaxSize,
            Integer chunkOverlap, String chunkUnit, String blockifyLlmProvider,
            String blockifyLlmModel, Boolean blockifyPiiMaskingEnabled, String embeddingProfileId,
            String embeddingProvider, String embeddingModel, Integer embeddingDimension,
            Boolean useLlmKeywordExtraction, String skillExtractionMode, Boolean generateSkillEmbeddings,
            String skillEmbeddingProvider, String skillEmbeddingModel, Integer skillEmbeddingDimension,
            Boolean ocrRequired, String ocrLanguage, String ocrMode, Boolean mathVisionCorrection) {
        this(fromStage, runChunking, runRagIndex, runSkillExtraction,
                chunkingStrategy, chunkMaxSize, chunkOverlap, chunkUnit,
                blockifyLlmProvider, blockifyLlmModel, blockifyPiiMaskingEnabled,
                null, null, embeddingProfileId, embeddingProvider, embeddingModel, embeddingDimension,
                useLlmKeywordExtraction, skillExtractionMode, generateSkillEmbeddings,
                skillEmbeddingProvider, skillEmbeddingModel, skillEmbeddingDimension,
                ocrRequired, ocrLanguage, ocrMode, mathVisionCorrection);
    }
}
