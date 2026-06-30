package studio.one.platform.markdown.web;

public record MarkdownIdeaBlockMergeUndoRequest(
        String mergedChunkId,
        String planFingerprint,
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
