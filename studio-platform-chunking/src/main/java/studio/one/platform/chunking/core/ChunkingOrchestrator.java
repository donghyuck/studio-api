package studio.one.platform.chunking.core;

import java.util.List;

/**
 * Coordinates strategy selection and composition for a chunking request.
 */
public interface ChunkingOrchestrator {

    List<Chunk> chunk(ChunkingContext context);
}
