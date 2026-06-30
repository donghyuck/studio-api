package studio.one.platform.markdown.web;

public record MarkdownIdeaBlockMergeAutoApplyRequest(
        String clusterId,
        Boolean preferEmbeddingClusters,
        String llmProvider,
        String llmModel,
        Integer maxClusters,
        Boolean runRagIndex,
        Boolean runSkillExtraction,
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
}
