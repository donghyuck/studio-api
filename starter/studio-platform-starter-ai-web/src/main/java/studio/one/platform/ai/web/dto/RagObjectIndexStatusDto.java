package studio.one.platform.ai.web.dto;

import java.time.Instant;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;

public record RagObjectIndexStatusDto(
        String objectType,
        String objectId,
        String jobId,
        String status,
        String currentStep,
        Double progress,
        int chunkCount,
        int embeddedCount,
        int indexedCount,
        int warningCount,
        Instant updatedAt) {

    public static RagObjectIndexStatusDto notRequested(String objectType, String objectId) {
        return new RagObjectIndexStatusDto(
                objectType, objectId, null, "NOT_REQUESTED", null, null,
                0, 0, 0, 0, null);
    }

    public static RagObjectIndexStatusDto from(RagIndexJob job) {
        return new RagObjectIndexStatusDto(
                job.objectType(),
                job.objectId(),
                job.jobId(),
                job.status().name(),
                job.currentStep() == null ? null : job.currentStep().name(),
                progress(job),
                job.chunkCount(),
                job.embeddedCount(),
                job.indexedCount(),
                job.warningCount(),
                updatedAt(job));
    }

    private static Double progress(RagIndexJob job) {
        if (job.status() == RagIndexJobStatus.PENDING) {
            return null;
        }
        if (job.status() == RagIndexJobStatus.SUCCEEDED || job.status() == RagIndexJobStatus.WARNING) {
            return 1.0d;
        }
        if (job.chunkCount() <= 0) {
            return 0.0d;
        }
        return Math.min(1.0d, job.indexedCount() / (double) job.chunkCount());
    }

    private static Instant updatedAt(RagIndexJob job) {
        if (job.finishedAt() != null) {
            return job.finishedAt();
        }
        if (job.startedAt() != null) {
            return job.startedAt();
        }
        return job.createdAt();
    }
}
