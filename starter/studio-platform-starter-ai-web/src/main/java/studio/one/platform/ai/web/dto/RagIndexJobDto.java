package studio.one.platform.ai.web.dto;

import java.time.Instant;

import studio.one.platform.ai.core.rag.RagEmbeddingSelectionInfo;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public record RagIndexJobDto(
        String jobId,
        String objectType,
        String objectId,
        String documentId,
        String sourceType,
        String sourceName,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        RagIndexJobStatus status,
        RagIndexJobStep currentStep,
        int chunkCount,
        int embeddedCount,
        int indexedCount,
        int warningCount,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String embeddingDeploymentId,
        String catalogId,
        String embeddingSpaceId) {

    public static RagIndexJobDto from(RagIndexJob job) {
        return from(job, null);
    }

    public static RagIndexJobDto from(RagIndexJob job, RagEmbeddingSelectionInfo embeddingSelection) {
        return new RagIndexJobDto(
                job.jobId(),
                job.objectType(),
                job.objectId(),
                job.documentId(),
                job.sourceType(),
                sourceName(job),
                embeddingSelection == null ? null : embeddingSelection.embeddingProfileId(),
                embeddingSelection == null ? null : embeddingSelection.embeddingProvider(),
                embeddingSelection == null ? null : embeddingSelection.embeddingModel(),
                job.status(),
                job.currentStep(),
                job.chunkCount(),
                job.embeddedCount(),
                job.indexedCount(),
                job.warningCount(),
                job.errorMessage(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                job.durationMs(),
                embeddingSelection == null || embeddingSelection.embeddingDeploymentId() == null
                        ? job.embeddingDeploymentId() : embeddingSelection.embeddingDeploymentId(),
                embeddingSelection == null || embeddingSelection.catalogId() == null
                        ? job.catalogId() : embeddingSelection.catalogId(),
                embeddingSelection == null || embeddingSelection.embeddingSpaceId() == null
                        ? job.embeddingSpaceId() : embeddingSelection.embeddingSpaceId());
    }

    private static String sourceName(RagIndexJob job) {
        return job.sourceName() == null ? job.documentId() : job.sourceName();
    }
}
