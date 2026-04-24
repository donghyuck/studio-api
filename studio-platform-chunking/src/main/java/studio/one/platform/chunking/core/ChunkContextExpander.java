package studio.one.platform.chunking.core;

/**
 * Expands a retrieved chunk into a larger answer context without calling
 * embedding APIs, vector stores, LLMs, or parsers.
 */
public interface ChunkContextExpander {

    ChunkContextExpansionStrategy strategy();

    ChunkContextExpansion expand(ChunkContextExpansionRequest request);
}
