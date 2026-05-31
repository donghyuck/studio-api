package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;

public record SkillGraphBatchJobView(
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
        String requestSnapshot,
        String errorMessage,
        String createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt) {

    public static SkillGraphBatchJobView from(SkillGraphBatchJob job) {
        return new SkillGraphBatchJobView(
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
                job.requestSnapshot(),
                job.errorMessage(),
                job.createdBy(),
                job.createdAt(),
                job.startedAt(),
                job.updatedAt(),
                job.completedAt());
    }
}
