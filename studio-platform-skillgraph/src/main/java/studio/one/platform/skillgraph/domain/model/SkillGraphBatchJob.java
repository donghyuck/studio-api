package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillGraphBatchJob(
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

    public SkillGraphBatchJob withStatus(
            SkillGraphBatchJobStatus nextStatus,
            String message,
            Instant updatedAt,
            Instant completedAt) {
        return new SkillGraphBatchJob(
                jobId,
                jobType,
                nextStatus,
                totalCount,
                requestedCount,
                processedCount,
                resultCount,
                failedCount,
                skippedCount,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                requestSnapshot,
                message,
                createdBy,
                createdAt,
                startedAt,
                updatedAt,
                completedAt);
    }

    public SkillGraphBatchJob withProgress(
            SkillGraphBatchJobStatus nextStatus,
            long processed,
            long results,
            long failed,
            long skipped,
            String message,
            Instant updatedAt,
            Instant completedAt) {
        return new SkillGraphBatchJob(
                jobId,
                jobType,
                nextStatus,
                totalCount,
                requestedCount,
                processed,
                results,
                failed,
                skipped,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                requestSnapshot,
                message,
                createdBy,
                createdAt,
                startedAt,
                updatedAt,
                completedAt);
    }

    public SkillGraphBatchJob markStarted(Instant startedAt, String message) {
        return new SkillGraphBatchJob(
                jobId,
                jobType,
                SkillGraphBatchJobStatus.RUNNING,
                totalCount,
                requestedCount,
                processedCount,
                resultCount,
                failedCount,
                skippedCount,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                requestSnapshot,
                message,
                createdBy,
                createdAt,
                startedAt,
                startedAt,
                null);
    }
}
