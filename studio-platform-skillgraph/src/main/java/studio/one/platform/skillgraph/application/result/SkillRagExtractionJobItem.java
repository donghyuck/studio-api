package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

public record SkillRagExtractionJobItem(
        String jobId,
        String chunkId,
        String documentId,
        String sourceId,
        String sourceChunkId,
        int extractedCount,
        SkillRagExtractionItemStatus status,
        String error,
        Instant createdAt,
        Instant updatedAt) {
}
