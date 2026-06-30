package studio.one.platform.markdown.application;

public record MarkdownIdeaBlockMergePreviewOptions(
        String clusterId,
        boolean preferEmbeddingClusters,
        String llmProvider,
        String llmModel,
        int maxClusters) {

    public MarkdownIdeaBlockMergePreviewOptions {
        clusterId = normalize(clusterId);
        llmProvider = normalize(llmProvider);
        llmModel = normalize(llmModel);
        maxClusters = maxClusters <= 0 ? 5 : Math.min(maxClusters, 20);
    }

    public static MarkdownIdeaBlockMergePreviewOptions defaults() {
        return new MarkdownIdeaBlockMergePreviewOptions(null, true, null, null, 5);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
