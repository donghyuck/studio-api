package studio.one.platform.skillgraph.application.result;

public record SkillDictionaryEmbeddingResult(
        int totalMissingCount,
        int requestedCount,
        int processedCount,
        int skippedCount,
        int failedCount,
        String jobId,
        SkillDictionaryEmbeddingJobStatus status,
        String message) {
}
