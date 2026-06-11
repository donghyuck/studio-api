package studio.one.platform.skillgraph.application.result;

public record ResolvedRagChunk(
        String chunkId,
        String documentId,
        String objectId,
        String content,
        Integer chunkOrder,
        Integer page,
        String section,
        Integer tokenCount,
        String warningStatus) {

    public ResolvedRagChunk(String chunkId, String documentId, String content) {
        this(chunkId, documentId, null, content, null, null, null, null, null);
    }

    public ResolvedRagChunk(
            String chunkId,
            String documentId,
            String content,
            Integer chunkOrder,
            Integer page,
            String section,
            Integer tokenCount,
            String warningStatus) {
        this(chunkId, documentId, null, content, chunkOrder, page, section, tokenCount, warningStatus);
    }
}
