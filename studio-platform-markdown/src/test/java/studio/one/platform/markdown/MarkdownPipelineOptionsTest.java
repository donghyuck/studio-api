package studio.one.platform.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import studio.one.platform.markdown.application.MarkdownPipelineOptions;

class MarkdownPipelineOptionsTest {

    @Test
    void acceptsBlockifyChunkingStrategy() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true, false, false,
                "BLOCKIFY", null, null, null,
                null, null, null, null);

        assertEquals("blockify", options.chunkingStrategy());
    }

    @Test
    void rejectsUnknownChunkingStrategy() {
        assertThrows(IllegalArgumentException.class, () -> new MarkdownPipelineOptions(
                true, false, false,
                "unknown", null, null, null,
                null, null, null, null));
    }
}
