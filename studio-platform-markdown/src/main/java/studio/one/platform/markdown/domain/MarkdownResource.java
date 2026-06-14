package studio.one.platform.markdown.domain;

public record MarkdownResource(
        String resourceId,
        String revisionId,
        String resourceType,
        String name,
        Long attachmentId,
        String metadataJson) {
}
