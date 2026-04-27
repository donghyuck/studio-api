package studio.one.platform.chunking.core;

import java.util.List;

/**
 * Chunker extension for structure-aware normalized documents.
 */
public interface NormalizedDocumentChunker extends Chunker {

    List<Chunk> chunk(NormalizedDocument document, ChunkingContext context);

    default List<Chunk> chunk(NormalizedDocument document) {
        if (document == null || document.chunkableText().isBlank()) {
            return List.of();
        }
        return chunk(document, document.toContextBuilder()
                .strategy(strategy())
                .build());
    }
}
