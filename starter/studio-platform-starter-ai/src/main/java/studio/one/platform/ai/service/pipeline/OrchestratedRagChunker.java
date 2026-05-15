package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.vector.VectorRecord;
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
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(request.metadata());
        putIfPresent(metadata, VectorRecord.KEY_EMBEDDING_PROFILE_ID, request.embeddingProfileId());
        putIfPresent(metadata, VectorRecord.KEY_EMBEDDING_PROVIDER, request.embeddingProvider());
        putIfPresent(metadata, VectorRecord.KEY_EMBEDDING_MODEL, request.embeddingModel());
        ChunkingContext context = ChunkingContext.configuredDefaults(indexedText)
                .sourceDocumentId(request.documentId())
                .contentType(RagChunkingMetadata.normalizeObjectScope(metadata.get("contentType")))
                .filename(RagChunkingMetadata.normalizeObjectScope(metadata.get("filename")))
                .objectType(RagChunkingMetadata.normalizeObjectScope(metadata.get("objectType")))
                .objectId(RagChunkingMetadata.normalizeObjectScope(metadata.get("objectId")))
                .metadata(metadata)
                .build();
        return chunkingOrchestrator.chunk(context).stream()
                .map(this::toPipelineChunk)
                .toList();
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.putIfAbsent(key, value);
        }
    }

    private RagPipelineChunk toPipelineChunk(Chunk chunk) {
        return new RagPipelineChunk(chunk.id(), chunk.content(), chunk.metadata().toMap());
    }

}
