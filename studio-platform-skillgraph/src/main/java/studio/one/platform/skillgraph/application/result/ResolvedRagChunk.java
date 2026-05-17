package studio.one.platform.skillgraph.application.result;

public record ResolvedRagChunk(
        String chunkId,
        String documentId,
        String content) {
}
