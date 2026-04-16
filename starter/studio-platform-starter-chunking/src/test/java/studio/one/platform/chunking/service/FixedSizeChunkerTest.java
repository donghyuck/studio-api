package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;

class FixedSizeChunkerTest {

    @Test
    void splitsIntoFixedSizeChunksWithDeterministicIds() {
        FixedSizeChunker chunker = new FixedSizeChunker(5, 0);

        List<Chunk> chunks = chunker.chunk(context("doc-1", "abcdefghijkl", 5, 0));

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(Chunk::id).containsExactly("doc-1-0", "doc-1-1", "doc-1-2");
        assertThat(chunks).extracting(Chunk::content).containsExactly("abcde", "fghij", "kl");
        assertThat(chunks.get(0).metadata().strategy()).isEqualTo(ChunkingStrategyType.FIXED_SIZE);
        assertThat(chunks.get(0).metadata().order()).isZero();
    }

    @Test
    void appliesOverlapWithoutStalling() {
        FixedSizeChunker chunker = new FixedSizeChunker(5, 2);

        List<Chunk> chunks = chunker.chunk(context("doc-1", "abcdefgh", 5, 2));

        assertThat(chunks).extracting(Chunk::content).containsExactly("abcde", "defgh");
    }

    private ChunkingContext context(String sourceDocumentId, String text, int maxSize, int overlap) {
        return ChunkingContext.builder(text)
                .sourceDocumentId(sourceDocumentId)
                .strategy(ChunkingStrategyType.FIXED_SIZE)
                .maxSize(maxSize)
                .overlap(overlap)
                .build();
    }
}
