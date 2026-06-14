package studio.one.platform.markdown.domain;

import java.time.Instant;

public record MarkdownRevision(
        String revisionId,
        String documentId,
        long sourceAttachmentId,
        Long resultAttachmentId,
        String documentConvertJobId,
        String extractorType,
        String extractorVersion,
        String optionsJson,
        String optionsHash,
        String sourceContentHash,
        String contentHash,
        String markdownText,
        String sourceFileName,
        String sourceFormat,
        String sourceObjectType,
        String sourceObjectId,
        MarkdownRevisionStatus status,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {
}
