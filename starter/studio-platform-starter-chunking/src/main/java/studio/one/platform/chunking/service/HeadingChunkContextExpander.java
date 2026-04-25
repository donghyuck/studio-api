package studio.one.platform.chunking.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;
import studio.one.platform.chunking.core.ChunkMetadata;

public class HeadingChunkContextExpander implements ChunkContextExpander {

    @Override
    public ChunkContextExpansionStrategy strategy() {
        return ChunkContextExpansionStrategy.HEADING;
    }

    @Override
    public ChunkContextExpansion expand(ChunkContextExpansionRequest request) {
        Chunk seed = request.seedChunk();
        String section = seed.metadata().section();
        List<Chunk> contextChunks = ChunkContextExpansionSupport.sameSection(section, candidates(request));
        if (contextChunks.isEmpty()) {
            contextChunks = List.of(seed);
        }
        return ChunkContextExpansion.of(
                seed,
                contextChunks,
                strategy(),
                ChunkContextExpansionSupport.metadata(ChunkMetadata.KEY_SECTION, section));
    }

    private List<Chunk> candidates(ChunkContextExpansionRequest request) {
        if (request.availableChunks().contains(request.seedChunk())) {
            return request.availableChunks();
        }
        return Stream.concat(request.availableChunks().stream(), Stream.of(request.seedChunk()))
                .toList();
    }
}
