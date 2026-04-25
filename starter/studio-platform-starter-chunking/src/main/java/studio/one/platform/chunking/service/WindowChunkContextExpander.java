package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;

public class WindowChunkContextExpander implements ChunkContextExpander {

    @Override
    public ChunkContextExpansionStrategy strategy() {
        return ChunkContextExpansionStrategy.WINDOW;
    }

    @Override
    public ChunkContextExpansion expand(ChunkContextExpansionRequest request) {
        List<Chunk> previous = collectPrevious(request);
        List<Chunk> next = collectNext(request);
        List<Chunk> context = new ArrayList<>(previous.size() + next.size() + 1);
        context.addAll(previous);
        context.add(request.seedChunk());
        context.addAll(next);
        return ChunkContextExpansion.of(
                request.seedChunk(),
                context,
                strategy(),
                Map.of("previousCount", previous.size(), "nextCount", next.size()));
    }

    private List<Chunk> collectPrevious(ChunkContextExpansionRequest request) {
        List<Chunk> chunks = new ArrayList<>();
        Chunk current = request.seedChunk();
        for (int i = 0; i < request.previousWindow(); i++) {
            String previousChunkId = current.metadata().previousChunkId();
            if (previousChunkId == null || previousChunkId.isBlank()) {
                break;
            }
            Chunk previous = request.chunkById(previousChunkId).orElse(null);
            if (previous == null || request.seedChunk().id().equals(previous.id()) || chunks.contains(previous)) {
                break;
            }
            chunks.add(previous);
            current = previous;
        }
        Collections.reverse(chunks);
        return chunks;
    }

    private List<Chunk> collectNext(ChunkContextExpansionRequest request) {
        List<Chunk> chunks = new ArrayList<>();
        Chunk current = request.seedChunk();
        for (int i = 0; i < request.nextWindow(); i++) {
            String nextChunkId = current.metadata().nextChunkId();
            if (nextChunkId == null || nextChunkId.isBlank()) {
                break;
            }
            Chunk next = request.chunkById(nextChunkId).orElse(null);
            if (next == null || request.seedChunk().id().equals(next.id()) || chunks.contains(next)) {
                break;
            }
            chunks.add(next);
            current = next;
        }
        return chunks;
    }
}
