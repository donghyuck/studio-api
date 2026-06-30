package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                metadata(previous.size(), next.size()));
    }

    private List<Chunk> collectPrevious(ChunkContextExpansionRequest request) {
        List<Chunk> chunks = new ArrayList<>();
        Chunk current = request.seedChunk();
        Set<String> visited = new HashSet<>();
        visited.add(request.seedChunk().id());
        for (int i = 0; i < request.previousWindow(); i++) {
            String previousChunkId = current.metadata().previousChunkId();
            if (previousChunkId == null || previousChunkId.isBlank()) {
                break;
            }
            Chunk previous = request.chunkById(previousChunkId).orElse(null);
            if (previous == null || !visited.add(previous.id())) {
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
        Set<String> visited = new HashSet<>();
        visited.add(request.seedChunk().id());
        for (int i = 0; i < request.nextWindow(); i++) {
            String nextChunkId = current.metadata().nextChunkId();
            if (nextChunkId == null || nextChunkId.isBlank()) {
                break;
            }
            Chunk next = request.chunkById(nextChunkId).orElse(null);
            if (next == null || !visited.add(next.id())) {
                break;
            }
            chunks.add(next);
            current = next;
        }
        return chunks;
    }

    private Map<String, Object> metadata(int previousCount, int nextCount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(ChunkContextExpansionSupport.metadata("previousCount", previousCount));
        metadata.putAll(ChunkContextExpansionSupport.metadata("nextCount", nextCount));
        return metadata;
    }
}
