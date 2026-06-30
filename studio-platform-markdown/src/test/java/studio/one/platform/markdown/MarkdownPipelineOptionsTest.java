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

    @Test
    void acceptsBlockifyLlmSelectionForBlockifyStrategy() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true, false, false,
                "blockify", null, null, null,
                "google-ai-gemini", "gemini-2.5-flash", null,
                null, null, null, null,
                false, null, false, null, null, null);

        assertEquals("google-ai-gemini", options.blockifyLlmProvider());
        assertEquals("gemini-2.5-flash", options.blockifyLlmModel());
    }

    @Test
    void rejectsBlockifyLlmSelectionForNonBlockifyStrategy() {
        assertThrows(IllegalArgumentException.class, () -> new MarkdownPipelineOptions(
                true, false, false,
                "structure-based", null, null, null,
                "google-ai-gemini", "gemini-2.5-flash", null,
                null, null, null, null,
                false, null, false, null, null, null));
    }

    @Test
    void acceptsBlockifyPiiMaskingSelectionForBlockifyStrategy() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true, false, false,
                "blockify", null, null, null,
                null, null, Boolean.FALSE,
                null, null, null, null,
                false, null, false, null, null, null);

        assertEquals(Boolean.FALSE, options.blockifyPiiMaskingEnabled());
    }
}
