package studio.one.platform.markdown.domain;

import java.time.Instant;

public record MarkdownDocument(
        String documentId,
        long sourceAttachmentId,
        String currentRevisionId,
        Instant createdAt,
        Instant updatedAt) {
}
