package studio.one.platform.autoconfigure.skillgraph.realtime;

import java.time.Instant;

import studio.one.platform.realtime.stomp.domain.model.RealtimePayload;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;

public record SkillGraphBatchJobPayload(
        String jobId,
        SkillGraphBatchJobType jobType,
        SkillGraphBatchJobStatus status,
        long totalCount,
        long requestedCount,
        long processedCount,
        long resultCount,
        long failedCount,
        long skippedCount,
        String embeddingProvider,
        String embeddingModel,
        int embeddingDimension,
        String message,
        Instant updatedAt) implements RealtimePayload {

    public static SkillGraphBatchJobPayload from(SkillGraphBatchJob job) {
        return new SkillGraphBatchJobPayload(
                job.jobId(),
                job.jobType(),
                job.status(),
                job.totalCount(),
                job.requestedCount(),
                job.processedCount(),
                job.resultCount(),
                job.failedCount(),
                job.skippedCount(),
                job.embeddingProvider(),
                job.embeddingModel(),
                job.embeddingDimension(),
                job.errorMessage(),
                job.updatedAt());
    }
}
