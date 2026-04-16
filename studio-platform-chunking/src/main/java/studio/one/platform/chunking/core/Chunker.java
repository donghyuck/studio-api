package studio.one.platform.chunking.core;

import java.util.List;

/**
 * Strategy implementation contract for creating chunks from a source context.
 */
public interface Chunker {

    ChunkingStrategyType strategy();

    List<Chunk> chunk(ChunkingContext context);
}
