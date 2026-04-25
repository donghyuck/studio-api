package studio.one.platform.ai.core.chunk;

import java.util.List;

/**
 * Splits text into manageable chunks suitable for embedding.
 *
 * @deprecated since 2.x. Use
 * {@code studio.one.platform.chunking.core.ChunkingOrchestrator} and
 * {@code studio.one.platform.chunking.core.ChunkingContext} from
 * {@code studio-platform-chunking} for new RAG indexing code. This interface is
 * retained for legacy fallback compatibility in {@code starter-ai}.
 */
@Deprecated(since = "2.x", forRemoval = false)
public interface TextChunker {

    List<TextChunk> chunk(String documentId, String text);
}
