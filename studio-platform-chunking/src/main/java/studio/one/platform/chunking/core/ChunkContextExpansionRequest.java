package studio.one.platform.chunking.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Input for expanding a retrieval child chunk into a larger answer context.
 */
public record ChunkContextExpansionRequest(
        Chunk seedChunk,
        List<Chunk> availableChunks,
        int previousWindow,
        int nextWindow,
        boolean includeParentContent,
        Map<String, Object> options) {

    public ChunkContextExpansionRequest {
        seedChunk = Objects.requireNonNull(seedChunk, "seedChunk must not be null");
        availableChunks = sanitizeChunks(availableChunks);
        if (previousWindow < 0) {
            throw new IllegalArgumentException("previousWindow must not be negative");
        }
        if (nextWindow < 0) {
            throw new IllegalArgumentException("nextWindow must not be negative");
        }
        options = sanitize(options);
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
        return chunks.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<String, Object> sanitize(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            if (value instanceof String stringValue && stringValue.isBlank()) {
                return;
            }
            sanitized.put(key, value);
        });
        return Map.copyOf(sanitized);
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
}
