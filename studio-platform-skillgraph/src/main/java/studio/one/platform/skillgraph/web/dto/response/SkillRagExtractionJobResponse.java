package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;
import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;

public record SkillRagExtractionJobResponse(
        String jobId,
        String objectType,
        String objectId,
        String documentId,
        String q,
        String mode,
        String candidateExtractorMode,
        List<String> chunkIds,
        String status,
        String executionStatus,
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
        return from(job, job.status().name());
    }

    public static SkillRagExtractionJobResponse from(SkillRagExtractionJob job, String executionStatus) {
        return new SkillRagExtractionJobResponse(
                job.jobId(),
                job.objectType(),
                job.objectId(),
                job.documentId(),
                job.query(),
                job.extractionMode(),
                job.candidateExtractorMode(),
                job.selectedChunkIds(),
                job.status().name(),
                executionStatus,
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
