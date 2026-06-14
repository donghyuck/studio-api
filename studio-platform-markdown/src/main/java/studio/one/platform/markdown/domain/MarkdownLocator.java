package studio.one.platform.markdown.domain;

public record MarkdownLocator(
        String locatorId,
        String revisionId,
        String locatorType,
        Integer locatorNo,
        String title,
        int startOffset,
        int endOffset,
        String sourceRef,
        String metadataJson) {
}
