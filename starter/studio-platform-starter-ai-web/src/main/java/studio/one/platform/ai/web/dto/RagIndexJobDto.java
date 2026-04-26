package studio.one.platform.ai.web.dto;

import java.time.Instant;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public record RagIndexJobDto(
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

    public static RagIndexJobDto from(RagIndexJob job) {
        return new RagIndexJobDto(
                job.jobId(),
                job.objectType(),
                job.objectId(),
                job.documentId(),
                job.sourceType(),
                job.status(),
                job.currentStep(),
                job.chunkCount(),
                job.embeddedCount(),
                job.indexedCount(),
                job.warningCount(),
                job.errorMessage(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                job.durationMs());
    }
}
