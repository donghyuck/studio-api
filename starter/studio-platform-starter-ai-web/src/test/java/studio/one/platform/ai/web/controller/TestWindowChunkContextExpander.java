package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;

final class TestWindowChunkContextExpander implements ChunkContextExpander {

    @Override
    public ChunkContextExpansionStrategy strategy() {
        return ChunkContextExpansionStrategy.WINDOW;
    }

    @Override
    public ChunkContextExpansion expand(ChunkContextExpansionRequest request) {
        String content = request.availableChunks().stream()
                .map(Chunk::content)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(request.seedChunk().content());
        return new ChunkContextExpansion(
                request.seedChunk(),
                request.availableChunks(),
                content,
                strategy(),
                Map.of());
    }

    static List<ChunkContextExpander> asList() {
        return List.of(new TestWindowChunkContextExpander());
    }
}
