package studio.one.platform.skillgraph.web.dto.response;

public record SkillRagChunkPreviewDto(
        String chunkId,
        String documentId,
        Integer chunkOrder,
        Integer page,
        String section,
        String textPreview,
        Integer tokenCount,
        int textLength,
        String warningStatus) {
}
