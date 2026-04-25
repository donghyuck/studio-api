package studio.one.platform.chunking.service;

import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;

public class TableChunkContextExpander implements ChunkContextExpander {

    @Override
    public ChunkContextExpansionStrategy strategy() {
        return ChunkContextExpansionStrategy.TABLE;
    }

    @Override
    public ChunkContextExpansion expand(ChunkContextExpansionRequest request) {
        Chunk seed = request.seedChunk();
        Map<String, Object> metadata = ChunkContextExpansionSupport.metadata(
                ChunkMetadata.KEY_CHUNK_TYPE,
                ChunkContextExpansionSupport.chunkType(seed).value());
        if (ChunkContextExpansionSupport.chunkType(seed) != ChunkType.TABLE) {
            return ChunkContextExpansion.of(seed, List.of(seed), strategy(), metadata);
        }
        String parentContent = request.includeParentContent()
                ? ChunkContextExpansionSupport.parentContent(seed)
                : "";
        if (!parentContent.isBlank()) {
            return new ChunkContextExpansion(seed, List.of(seed), parentContent, strategy(), metadata);
        }
        return ChunkContextExpansion.of(seed, List.of(seed), strategy(), metadata);
    }
}
