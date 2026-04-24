package studio.one.platform.chunking.core;

import java.util.List;

/**
 * Coordinates strategy selection and composition for a chunking request.
 */
public interface ChunkingOrchestrator {

    List<Chunk> chunk(ChunkingContext context);

    default List<Chunk> chunk(NormalizedDocument document) {
        if (document == null || document.chunkableText().isBlank()) {
            return List.of();
        }
        return chunk(document.toContextBuilder().build());
    }
}
