package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

public record SkillDictionaryEmbeddingJob(
        String jobId,
        SkillDictionaryEmbeddingJobStatus status,
        int totalCount,
        int requestedCount,
        int processedCount,
        int failedCount,
        int skippedCount,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt,
        String message) {

    public SkillDictionaryEmbeddingJob withStatus(
            SkillDictionaryEmbeddingJobStatus status,
            String message,
            Instant updatedAt) {
        return new SkillDictionaryEmbeddingJob(
                jobId,
                status,
                totalCount,
                requestedCount,
                processedCount,
                failedCount,
                skippedCount,
                startedAt,
                updatedAt,
                completedAt,
                message);
    }

    public SkillDictionaryEmbeddingJob withProgress(
            SkillDictionaryEmbeddingJobStatus status,
            int processedCount,
            int failedCount,
            int skippedCount,
            Instant updatedAt,
            Instant completedAt,
            String message) {
        return new SkillDictionaryEmbeddingJob(
                jobId,
                status,
                totalCount,
                requestedCount,
                processedCount,
                failedCount,
                skippedCount,
                startedAt,
                updatedAt,
                completedAt,
                message);
    }
}
