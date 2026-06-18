package studio.one.platform.markdown.domain;

import java.time.Instant;

public record MarkdownExtractPart(
        String partId,
        String revisionId,
        int pageFrom,
        int pageTo,
        String status,
        String engine,
        int textLength,
        String markdownText,
        String errorCode,
        String errorMessage,
        Long elapsedMs,
        String metadataJson,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
}
