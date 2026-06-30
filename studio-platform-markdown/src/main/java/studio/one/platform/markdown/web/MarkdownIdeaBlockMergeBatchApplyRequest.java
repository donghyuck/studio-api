package studio.one.platform.markdown.web;

import java.util.List;

public record MarkdownIdeaBlockMergeBatchApplyRequest(
        List<MarkdownIdeaBlockMergeApplyRequest> items,
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
