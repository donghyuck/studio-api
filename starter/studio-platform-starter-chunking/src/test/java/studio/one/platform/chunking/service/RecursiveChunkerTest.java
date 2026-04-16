package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;

class RecursiveChunkerTest {

    @Test
    void prefersParagraphBoundariesBeforeFixedSizeFallback() {
        RecursiveChunker chunker = new RecursiveChunker(12, 0);

        List<Chunk> chunks = chunker.chunk(context("doc", "alpha beta\n\ngamma delta", 12, 0));

        assertThat(chunks).extracting(Chunk::content).containsExactly("alpha beta", "gamma delta");
        assertThat(chunks).extracting(Chunk::id).containsExactly("doc-0", "doc-1");
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.RECURSIVE);
    }

    @Test
    void fallsBackToWhitespaceThenFixedSizeWhenNeeded() {
        RecursiveChunker chunker = new RecursiveChunker(6, 0);

        List<Chunk> chunks = chunker.chunk(context("doc", "alpha beta gamma", 6, 0));

        assertThat(chunks).extracting(Chunk::content).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void appliesOverlapBetweenPackedChunks() {
        RecursiveChunker chunker = new RecursiveChunker(10, 3);

        List<Chunk> chunks = chunker.chunk(context("doc", "alpha beta gamma", 10, 3));

        assertThat(chunks).extracting(Chunk::content).containsExactly("alpha beta", "eta gamma");
    }

    private ChunkingContext context(String sourceDocumentId, String text, int maxSize, int overlap) {
        return ChunkingContext.builder(text)
                .sourceDocumentId(sourceDocumentId)
                .maxSize(maxSize)
                .overlap(overlap)
                .build();
    }
}
