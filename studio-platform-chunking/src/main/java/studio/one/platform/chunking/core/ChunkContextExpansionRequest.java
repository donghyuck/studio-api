package studio.one.platform.chunking.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collections;

/**
 * Input for expanding a retrieval child chunk into a larger answer context.
 * {@code availableChunks} is expected to be a small, pre-filtered candidate set
 * around the seed chunk, not an entire corpus or unbounded retrieval result.
 */
public class ChunkContextExpansionRequest {
    private final Chunk seedChunk;
    private final List<Chunk> availableChunks;
    private final int previousWindow;
    private final int nextWindow;
    private final boolean includeParentContent;
    private final Map<String, Object> options;


    public ChunkContextExpansionRequest(
            Chunk seedChunk,
            List<Chunk> availableChunks,
            int previousWindow,
            int nextWindow,
            boolean includeParentContent,
            Map<String, Object> options) {
        seedChunk = Objects.requireNonNull(seedChunk, "seedChunk must not be null");
        availableChunks = sanitizeChunks(availableChunks);
        if (previousWindow < 0) {
            throw new IllegalArgumentException("previousWindow must not be negative");
        }
        if (nextWindow < 0) {
            throw new IllegalArgumentException("nextWindow must not be negative");
        }
        options = ChunkMetadataMaps.sanitize(options);
    
        this.seedChunk = seedChunk;
        this.availableChunks = availableChunks;
        this.previousWindow = previousWindow;
        this.nextWindow = nextWindow;
        this.includeParentContent = includeParentContent;
        this.options = options;
    }

    public static Builder builder(Chunk seedChunk) {
        return new Builder(seedChunk);
    }

    public Optional<Chunk> chunkById(String chunkId) {
        if (chunkId == null || chunkId.isBlank()) {
            return Optional.empty();
        }
        String normalized = chunkId.trim();
        return availableChunks.stream()
                .filter(chunk -> chunk.id().equals(normalized))
                .findFirst();
    }

    public Optional<Object> option(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(options.get(key));
    }

    private static List<Chunk> sanitizeChunks(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(chunks.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList()));
    }

    public static final class Builder {
        private final Chunk seedChunk;
        private List<Chunk> availableChunks = List.of();
        private int previousWindow;
        private int nextWindow;
        private boolean includeParentContent = true;
        private Map<String, Object> options = Map.of();

        private Builder(Chunk seedChunk) {
            this.seedChunk = seedChunk;
        }

        public Builder availableChunks(List<Chunk> availableChunks) {
            this.availableChunks = availableChunks;
            return this;
        }

        public Builder previousWindow(int previousWindow) {
            this.previousWindow = previousWindow;
            return this;
        }

        public Builder nextWindow(int nextWindow) {
            this.nextWindow = nextWindow;
            return this;
        }

        public Builder includeParentContent(boolean includeParentContent) {
            this.includeParentContent = includeParentContent;
            return this;
        }

        public Builder options(Map<String, Object> options) {
            this.options = options;
            return this;
        }

        public ChunkContextExpansionRequest build() {
            return new ChunkContextExpansionRequest(seedChunk, availableChunks, previousWindow, nextWindow,
                    includeParentContent, options);
        }
    }

    public Chunk seedChunk() {
        return seedChunk;
    }

    public List<Chunk> availableChunks() {
        return availableChunks;
    }

    public int previousWindow() {
        return previousWindow;
    }

    public int nextWindow() {
        return nextWindow;
    }

    public boolean includeParentContent() {
        return includeParentContent;
    }

    public Map<String, Object> options() {
        return options;
    }
}
