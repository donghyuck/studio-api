package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

public record SkillRagBatchExtractionResponse(
        String objectType,
        String objectId,
        String documentId,
        int requestedChunks,
        int resolvedChunks,
        int succeededChunks,
        int failedChunks,
        int extractedCount,
        List<SkillRagChunkExtractionItemDto> items) {
}
