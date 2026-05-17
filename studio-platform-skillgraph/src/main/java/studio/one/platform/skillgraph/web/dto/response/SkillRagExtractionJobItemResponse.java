package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;

public record SkillRagExtractionJobItemResponse(
        String jobId,
        String chunkId,
        String documentId,
        String sourceId,
        String sourceChunkId,
        int extractedCount,
        String status,
        String error,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillRagExtractionJobItemResponse from(SkillRagExtractionJobItem item) {
        return new SkillRagExtractionJobItemResponse(
                item.jobId(),
                item.chunkId(),
                item.documentId(),
                item.sourceId(),
                item.sourceChunkId(),
                item.extractedCount(),
                item.status().name(),
                item.error(),
                item.createdAt(),
                item.updatedAt());
    }
}
