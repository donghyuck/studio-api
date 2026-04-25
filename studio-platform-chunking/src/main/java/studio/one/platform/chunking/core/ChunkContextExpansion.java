package studio.one.platform.chunking.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Expanded context returned by a {@link ChunkContextExpander}.
 * The content is non-blank because {@link Chunk} also rejects blank content.
 */
public record ChunkContextExpansion(
        Chunk seedChunk,
        List<Chunk> contextChunks,
        String content,
        ChunkContextExpansionStrategy strategy,
        Map<String, Object> metadata) {

    public ChunkContextExpansion {
        seedChunk = Objects.requireNonNull(seedChunk, "seedChunk must not be null");
        contextChunks = sanitizeChunks(contextChunks);
        content = content == null ? "" : content.trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        strategy = strategy == null ? ChunkContextExpansionStrategy.UNKNOWN : strategy;
        metadata = ChunkMetadataMaps.sanitize(metadata);
    }

    public static ChunkContextExpansion of(
            Chunk seedChunk,
            List<Chunk> contextChunks,
            ChunkContextExpansionStrategy strategy) {
        return of(seedChunk, contextChunks, strategy, Map.of());
    }

    public static ChunkContextExpansion of(
            Chunk seedChunk,
            List<Chunk> contextChunks,
            ChunkContextExpansionStrategy strategy,
            Map<String, Object> metadata) {
        Objects.requireNonNull(seedChunk, "seedChunk must not be null");
        return new ChunkContextExpansion(seedChunk, contextChunks, joinedContent(contextChunks, seedChunk), strategy,
                metadata);
    }

    private static String joinedContent(List<Chunk> contextChunks, Chunk fallback) {
        List<Chunk> chunks = sanitizeChunks(contextChunks);
        if (chunks.isEmpty()) {
            return fallback.content();
        }
        return chunks.stream()
                .map(Chunk::content)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(fallback.content());
    }

    private static List<Chunk> sanitizeChunks(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .filter(Objects::nonNull)
                .toList();
    }

}
