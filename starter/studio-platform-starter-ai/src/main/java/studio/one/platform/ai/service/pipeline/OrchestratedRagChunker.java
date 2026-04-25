package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;

final class OrchestratedRagChunker implements RagChunker {

    private final ChunkingOrchestrator chunkingOrchestrator;

    OrchestratedRagChunker(ChunkingOrchestrator chunkingOrchestrator) {
        this.chunkingOrchestrator = Objects.requireNonNull(chunkingOrchestrator, "chunkingOrchestrator");
    }

    @Override
    public List<RagPipelineChunk> chunk(String indexedText, RagIndexRequest request) {
        Map<String, Object> metadata = request.metadata();
        ChunkingContext context = ChunkingContext.configuredDefaults(indexedText)
                .sourceDocumentId(request.documentId())
                .contentType(normalizeObjectScope(metadata.get("contentType")))
                .filename(normalizeObjectScope(metadata.get("filename")))
                .objectType(normalizeObjectScope(metadata.get("objectType")))
                .objectId(normalizeObjectScope(metadata.get("objectId")))
                .metadata(metadata)
                .build();
        return chunkingOrchestrator.chunk(context).stream()
                .map(this::toPipelineChunk)
                .toList();
    }

    private RagPipelineChunk toPipelineChunk(Chunk chunk) {
        return new RagPipelineChunk(chunk.id(), chunk.content(), chunk.metadata().toMap());
    }

    private String normalizeObjectScope(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
