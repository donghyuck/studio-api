package studio.one.platform.markdown.application;

import java.nio.file.Path;

public record MarkdownContent(
        String documentId,
        String revisionId,
        String filename,
        String contentHash,
        Path path,
        long contentLength) {
}
