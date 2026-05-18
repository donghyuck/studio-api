package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

public record SkillRagExtractionJob(
        String jobId,
        String objectType,
        String objectId,
        String documentId,
        SkillRagExtractionJobStatus status,
        int requestedChunks,
        int totalChunks,
        int processedChunks,
        int succeededChunks,
        int failedChunks,
        int extractedCount,
        String error,
        Instant createdAt,
        Instant updatedAt) {

    public SkillRagExtractionJob withStatus(SkillRagExtractionJobStatus status, String error, Instant now) {
        return new SkillRagExtractionJob(jobId, objectType, objectId, documentId, status, requestedChunks,
                totalChunks, processedChunks, succeededChunks, failedChunks, extractedCount, error, createdAt, now);
    }

    public SkillRagExtractionJob withProgress(
            SkillRagExtractionJobStatus status,
            int totalChunks,
            int processedChunks,
            int succeededChunks,
            int failedChunks,
            int extractedCount,
            String error,
            Instant now) {
        return new SkillRagExtractionJob(jobId, objectType, objectId, documentId, status, requestedChunks,
                totalChunks, processedChunks, succeededChunks, failedChunks, extractedCount, error, createdAt, now);
    }
}
