package studio.one.platform.ai.core.rag;

import java.time.Instant;
import java.util.Objects;

public record RagIndexJob(
        String jobId,
        String objectType,
        String objectId,
        String documentId,
        String sourceType,
        RagIndexJobStatus status,
        RagIndexJobStep currentStep,
        int chunkCount,
        int embeddedCount,
        int indexedCount,
        int warningCount,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs) {

    public RagIndexJob {
        jobId = requireText(jobId, "jobId");
        documentId = normalize(documentId);
        objectType = normalize(objectType);
        objectId = normalize(objectId);
        sourceType = normalize(sourceType);
        status = status == null ? RagIndexJobStatus.PENDING : status;
        chunkCount = Math.max(0, chunkCount);
        embeddedCount = Math.max(0, embeddedCount);
        indexedCount = Math.max(0, indexedCount);
        warningCount = Math.max(0, warningCount);
        errorMessage = normalize(errorMessage);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        if (durationMs != null && durationMs < 0L) {
            durationMs = 0L;
        }
    }

    public static RagIndexJob pending(
            String jobId,
            String objectType,
            String objectId,
            String documentId,
            String sourceType,
            Instant createdAt) {
        return new RagIndexJob(
                jobId,
                objectType,
                objectId,
                documentId,
                sourceType,
                RagIndexJobStatus.PENDING,
                null,
                0,
                0,
                0,
                0,
                null,
                createdAt,
                null,
                null,
                null);
    }

    public RagIndexJob withStatus(RagIndexJobStatus status, RagIndexJobStep step, String errorMessage, Instant now) {
        Instant nextStartedAt = startedAt;
        Instant nextFinishedAt = finishedAt;
        Long nextDurationMs = durationMs;
        if (status == RagIndexJobStatus.RUNNING && nextStartedAt == null) {
            nextStartedAt = now;
        }
        if (isTerminal(status)) {
            nextFinishedAt = now;
            if (nextStartedAt != null) {
                nextDurationMs = Math.max(0L, nextFinishedAt.toEpochMilli() - nextStartedAt.toEpochMilli());
            }
        } else {
            nextFinishedAt = null;
            nextDurationMs = null;
        }
        return new RagIndexJob(
                jobId,
                objectType,
                objectId,
                documentId,
                sourceType,
                status,
                step,
                chunkCount,
                embeddedCount,
                indexedCount,
                warningCount,
                errorMessage,
                createdAt,
                nextStartedAt,
                nextFinishedAt,
                nextDurationMs);
    }

    public RagIndexJob withCounts(Integer chunkCount, Integer embeddedCount, Integer indexedCount, Integer warningCount) {
        return new RagIndexJob(
                jobId,
                objectType,
                objectId,
                documentId,
                sourceType,
                status,
                currentStep,
                chunkCount == null ? this.chunkCount : chunkCount,
                embeddedCount == null ? this.embeddedCount : embeddedCount,
                indexedCount == null ? this.indexedCount : indexedCount,
                warningCount == null ? this.warningCount : warningCount,
                errorMessage,
                createdAt,
                startedAt,
                finishedAt,
                durationMs);
    }

    public RagIndexJob resetForRetry(Instant now) {
        return new RagIndexJob(
                jobId,
                objectType,
                objectId,
                documentId,
                sourceType,
                RagIndexJobStatus.PENDING,
                null,
                0,
                0,
                0,
                0,
                null,
                createdAt == null ? now : createdAt,
                null,
                null,
                null);
    }

    private static boolean isTerminal(RagIndexJobStatus status) {
        return status == RagIndexJobStatus.SUCCEEDED
                || status == RagIndexJobStatus.WARNING
                || status == RagIndexJobStatus.FAILED
                || status == RagIndexJobStatus.CANCELLED;
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        return Objects.requireNonNull(normalized, name + " must not be blank");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
