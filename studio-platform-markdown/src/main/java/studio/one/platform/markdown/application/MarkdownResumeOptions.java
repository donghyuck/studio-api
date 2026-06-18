package studio.one.platform.markdown.application;

import studio.one.platform.markdown.domain.MarkdownPipelineStage;

public record MarkdownResumeOptions(
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
        Integer skillEmbeddingDimension) {

    public MarkdownResumeOptions(
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
            Boolean generateSkillEmbeddings,
            String skillEmbeddingProvider,
            String skillEmbeddingModel,
            Integer skillEmbeddingDimension) {
        this(fromStage, runChunking, runRagIndex, runSkillExtraction,
                chunkingStrategy, chunkMaxSize, chunkOverlap, chunkUnit,
                embeddingProfileId, embeddingProvider, embeddingModel, embeddingDimension,
                useLlmKeywordExtraction, null, generateSkillEmbeddings,
                skillEmbeddingProvider, skillEmbeddingModel, skillEmbeddingDimension);
    }

    public static MarkdownResumeOptions fromStage(MarkdownPipelineStage stage) {
        return new MarkdownResumeOptions(
                stage,
                null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null);
    }
}
