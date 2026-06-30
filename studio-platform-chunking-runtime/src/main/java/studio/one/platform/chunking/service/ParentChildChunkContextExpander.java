package studio.one.platform.chunking.service;

import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;
import studio.one.platform.chunking.core.ChunkMetadata;

public class ParentChildChunkContextExpander implements ChunkContextExpander {

    @Override
    public ChunkContextExpansionStrategy strategy() {
        return ChunkContextExpansionStrategy.PARENT_CHILD;
    }

    @Override
    public ChunkContextExpansion expand(ChunkContextExpansionRequest request) {
        Chunk seed = request.seedChunk();
        String parentChunkId = seed.metadata().parentChunkId();
        List<Chunk> contextChunks = ChunkContextExpansionSupport.sameParent(parentChunkId,
                ChunkContextExpansionSupport.withSeed(seed, request.availableChunks()));
        if (contextChunks.isEmpty()) {
            contextChunks = List.of(seed);
        }
        Map<String, Object> metadata = ChunkContextExpansionSupport.metadata(
                ChunkMetadata.KEY_PARENT_CHUNK_ID,
                parentChunkId);
        String parentContent = request.includeParentContent()
                ? ChunkContextExpansionSupport.parentContent(seed)
                : "";
        if (!parentContent.isBlank()) {
            return new ChunkContextExpansion(seed, contextChunks, parentContent, strategy(), metadata);
        }
        return ChunkContextExpansion.of(seed, contextChunks, strategy(), metadata);
    }

}
