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
        boolean excludeExtracted,
        boolean generateEmbeddings,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        String embeddingJobId,
        String embeddingStatus,
        Instant createdAt,
        Instant updatedAt) {

    public SkillRagExtractionJob(
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
        this(jobId, objectType, objectId, documentId, status, requestedChunks, totalChunks, processedChunks,
                succeededChunks, failedChunks, extractedCount, error, false, false, null, null, null, null, null,
                createdAt, updatedAt);
    }

    public SkillRagExtractionJob withStatus(SkillRagExtractionJobStatus status, String error, Instant now) {
        return new SkillRagExtractionJob(jobId, objectType, objectId, documentId, status, requestedChunks,
                totalChunks, processedChunks, succeededChunks, failedChunks, extractedCount, error, excludeExtracted,
                generateEmbeddings, embeddingProvider, embeddingModel, embeddingDimension, embeddingJobId,
                embeddingStatus, createdAt, now);
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
                totalChunks, processedChunks, succeededChunks, failedChunks, extractedCount, error, excludeExtracted,
                generateEmbeddings, embeddingProvider, embeddingModel, embeddingDimension, embeddingJobId,
                embeddingStatus, createdAt, now);
    }

    public SkillRagExtractionJob withEmbeddingJob(
            SkillRagExtractionJobStatus status,
            String embeddingJobId,
            String embeddingStatus,
            String error,
            Instant now) {
        return new SkillRagExtractionJob(jobId, objectType, objectId, documentId, status, requestedChunks,
                totalChunks, processedChunks, succeededChunks, failedChunks, extractedCount, error, excludeExtracted,
                generateEmbeddings, embeddingProvider, embeddingModel, embeddingDimension, embeddingJobId,
                embeddingStatus, createdAt, now);
    }
}
