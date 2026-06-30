package studio.one.platform.markdown.web;

public record MarkdownIdeaBlockMergePreviewRequest(
        String clusterId,
        Boolean preferEmbeddingClusters,
        String llmProvider,
        String llmModel,
        Integer maxClusters) {
}
