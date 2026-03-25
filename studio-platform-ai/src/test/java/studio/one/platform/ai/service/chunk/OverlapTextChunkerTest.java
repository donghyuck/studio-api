package studio.one.platform.ai.service.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.chunk.TextChunk;

public class OverlapTextChunkerTest {

    @Test
    public void splitsLongSingleParagraphIntoBoundedChunks() {
        OverlapTextChunker chunker = new OverlapTextChunker(10, 0);

        List<TextChunk> chunks = chunker.chunk("doc", "abcdefghijklmnop");

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(TextChunk::content)
                .containsExactly("abcdefghij", "klmnop");
        assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 10);
    }

    @Test
    public void carriesOverlapAcrossChunkBoundary() {
        OverlapTextChunker chunker = new OverlapTextChunker(10, 3);

        List<TextChunk> chunks = chunker.chunk("doc", "abcdef\n\nghij");

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(TextChunk::content)
                .containsExactly("abcdef", "def\n\nghij");
        assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 10);
    }

    @Test
    public void keepsParagraphBoundariesWhenContentFitsWithinChunkLimit() {
        OverlapTextChunker chunker = new OverlapTextChunker(32, 4);

        List<TextChunk> chunks = chunker.chunk("doc", "alpha\n\nbeta");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("alpha\n\nbeta");
    }
}
