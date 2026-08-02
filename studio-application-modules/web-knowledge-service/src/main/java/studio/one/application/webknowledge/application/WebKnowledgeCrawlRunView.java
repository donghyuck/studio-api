package studio.one.application.webknowledge.application;

import java.time.Instant;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunEntity;

public record WebKnowledgeCrawlRunView(
        String runId,
        String status,
        int discoveredCount,
        int fetchedCount,
        int indexedCount,
        int unchangedCount,
        int updatedCount,
        int removedCount,
        int failedCount,
        int skippedCount,
        long responseBytes,
        long normalizedChars,
        boolean truncated,
        String truncationReason,
        String errorCode,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static WebKnowledgeCrawlRunView from(WebKnowledgeCrawlRunEntity run) {
        return new WebKnowledgeCrawlRunView(
                run.runId(),
                run.status(),
                run.discoveredCount(),
                run.fetchedCount(),
                run.indexedCount(),
                run.unchangedCount(),
                run.updatedCount(),
                run.removedCount(),
                run.failedCount(),
                run.skippedCount(),
                run.responseBytes(),
                run.normalizedChars(),
                run.truncated(),
                run.truncationReason(),
                run.errorCode(),
                run.startedAt(),
                run.completedAt(),
                run.createdAt(),
                run.updatedAt());
    }
}
