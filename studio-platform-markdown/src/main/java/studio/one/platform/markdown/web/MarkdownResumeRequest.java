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
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        Boolean useLlmKeywordExtraction,
        String skillExtractionMode,
        Boolean generateSkillEmbeddings,
        String skillEmbeddingProvider,
        String skillEmbeddingModel,
        Integer skillEmbeddingDimension
) {
}
