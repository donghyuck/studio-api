package studio.one.platform.chunking.core;

import java.util.List;

/**
 * Coordinates strategy selection and composition for a chunking request.
 */
public interface ChunkingOrchestrator {

    List<Chunk> chunk(ChunkingContext context);

    default List<Chunk> chunk(NormalizedDocument document) {
        return chunk(document.toContextBuilder().build());
    }
}
