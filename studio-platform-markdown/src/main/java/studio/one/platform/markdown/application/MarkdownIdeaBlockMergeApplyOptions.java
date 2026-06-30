package studio.one.platform.markdown.application;

public record MarkdownIdeaBlockMergeApplyOptions(
        MarkdownIdeaBlockMergePreviewOptions previewOptions,
        String planFingerprint) {

    public MarkdownIdeaBlockMergeApplyOptions {
        previewOptions = previewOptions == null ? MarkdownIdeaBlockMergePreviewOptions.defaults() : previewOptions;
    }
}
