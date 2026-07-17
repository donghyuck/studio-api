package studio.one.platform.markdown.application;

import java.nio.file.Path;

public record MarkdownPagePreview(
        String documentId,
        String revisionId,
        int page,
        MarkdownPagePreviewBounds bounds,
        Path path,
        long contentLength) {
}
