package studio.one.platform.ai.core.chunk;

import java.util.List;

/**
 * Splits text into manageable chunks suitable for embedding.
 */
public interface TextChunker {

    List<TextChunk> chunk(String documentId, String text);
}
