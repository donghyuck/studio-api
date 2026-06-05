package studio.one.platform.autoconfigure.skillgraph.realtime;

import java.time.Instant;

import studio.one.platform.realtime.stomp.domain.model.RealtimePayload;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;

public record SkillRagExtractionJobPayload(
        String eventType,
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
        boolean generateEmbeddings,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        String embeddingJobId,
        String embeddingStatus,
        String error,
        Instant updatedAt) implements RealtimePayload {

    public static SkillRagExtractionJobPayload from(SkillRagExtractionJob job) {
        return new SkillRagExtractionJobPayload(
                "SKILL_RAG_EXTRACTION_JOB_UPDATED",
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
                job.generateEmbeddings(),
                job.embeddingProvider(),
                job.embeddingModel(),
                job.embeddingDimension(),
                job.embeddingJobId(),
                job.embeddingStatus(),
                job.error(),
                job.updatedAt());
    }
}
