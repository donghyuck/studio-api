package studio.one.platform.skillgraph.application.result;

public record ResolvedRagChunk(
        String chunkId,
        String documentId,
        String content,
        Integer chunkOrder,
        Integer page,
        String section,
        Integer tokenCount,
        String warningStatus) {

    public ResolvedRagChunk(String chunkId, String documentId, String content) {
        this(chunkId, documentId, content, null, null, null, null, null);
    }
}
