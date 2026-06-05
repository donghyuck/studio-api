package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;

public record SkillRagExtractionJobResponse(
        String jobId,
        String objectType,
        String objectId,
        String documentId,
        String status,
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

    public static SkillRagExtractionJobResponse from(SkillRagExtractionJob job) {
        return new SkillRagExtractionJobResponse(
                job.jobId(),
                job.objectType(),
                job.objectId(),
                job.documentId(),
                job.status().name(),
                job.requestedChunks(),
                job.totalChunks(),
                job.processedChunks(),
                job.succeededChunks(),
                job.failedChunks(),
                job.extractedCount(),
                job.error(),
                job.excludeExtracted(),
                job.generateEmbeddings(),
                job.embeddingProvider(),
                job.embeddingModel(),
                job.embeddingDimension(),
                job.embeddingJobId(),
                job.embeddingStatus(),
                job.createdAt(),
                job.updatedAt());
    }
}
