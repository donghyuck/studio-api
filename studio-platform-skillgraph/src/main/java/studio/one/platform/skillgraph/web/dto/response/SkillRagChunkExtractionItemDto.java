package studio.one.platform.skillgraph.web.dto.response;

public record SkillRagChunkExtractionItemDto(
        String chunkId,
        String documentId,
        String sourceId,
        String sourceChunkId,
        int extractedCount,
        String status,
        String error) {
}
