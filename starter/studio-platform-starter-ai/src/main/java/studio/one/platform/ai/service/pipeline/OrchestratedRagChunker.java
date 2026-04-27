package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.ai.core.rag.RagChunkingOptions;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.ChunkUnit;

final class OrchestratedRagChunker implements RagChunker {

    private final ChunkingOrchestrator chunkingOrchestrator;

    OrchestratedRagChunker(ChunkingOrchestrator chunkingOrchestrator) {
        this.chunkingOrchestrator = Objects.requireNonNull(chunkingOrchestrator, "chunkingOrchestrator");
    }

    @Override
    public List<RagPipelineChunk> chunk(String indexedText, RagIndexRequest request) {
        Map<String, Object> metadata = request.metadata();
        ChunkingContext.Builder builder = ChunkingContext.configuredDefaults(indexedText)
                .sourceDocumentId(request.documentId())
                .contentType(RagChunkingMetadata.normalizeObjectScope(metadata.get("contentType")))
                .filename(RagChunkingMetadata.normalizeObjectScope(metadata.get("filename")))
                .objectType(RagChunkingMetadata.normalizeObjectScope(metadata.get("objectType")))
                .objectId(RagChunkingMetadata.normalizeObjectScope(metadata.get("objectId")))
                .metadata(metadata);
        applyChunkingOptions(builder, request.chunkingOptions());
        ChunkingContext context = builder.build();
        return chunkingOrchestrator.chunk(context).stream()
                .map(this::toPipelineChunk)
                .toList();
    }

    private void applyChunkingOptions(ChunkingContext.Builder builder, RagChunkingOptions options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        if (options.strategy() != null) {
            builder.strategy(ChunkingStrategyType.from(options.strategy()));
        }
        if (options.maxSize() != null) {
            builder.maxSize(options.maxSize());
        }
        if (options.overlap() != null) {
            builder.overlap(options.overlap());
        }
        if (options.unit() != null) {
            builder.unit(ChunkUnit.from(options.unit()));
        }
    }

    private RagPipelineChunk toPipelineChunk(Chunk chunk) {
        return new RagPipelineChunk(chunk.id(), chunk.content(), chunk.metadata().toMap());
    }

}
