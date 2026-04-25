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
        ChunkType chunkType = ChunkContextExpansionSupport.chunkType(seed);
        Map<String, Object> metadata = ChunkContextExpansionSupport.metadata(
                ChunkMetadata.KEY_CHUNK_TYPE,
                chunkType.value());
        if (chunkType != ChunkType.TABLE) {
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
