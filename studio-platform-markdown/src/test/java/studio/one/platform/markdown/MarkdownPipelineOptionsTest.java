package studio.one.platform.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
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

    @Test
    void defaultsMathVisionCorrectionToFalse() {
        MarkdownPipelineOptions options = MarkdownPipelineOptions.none();

        assertEquals(Boolean.FALSE, options.mathVisionCorrection());
    }

    @Test
    void preservesMathVisionCorrectionOptIn() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                true, "kor+eng", "FORCE", true);

        assertEquals(Boolean.TRUE, options.mathVisionCorrection());
        assertEquals("FORCE", options.ocrMode());
    }

    @Test
    void appliesMathTextbookProfileDefaults() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                null, null, null, null,
                "MATH_TEXTBOOK", null, null);

        assertEquals("structure-based", options.chunkingStrategy());
        assertEquals(1200, options.chunkMaxSize());
        assertEquals(150, options.chunkOverlap());
        assertEquals("CHARACTER", options.chunkUnit());
        assertEquals(Boolean.TRUE, options.ocrRequired());
        assertEquals("kor+eng", options.ocrLanguage());
        assertEquals("FORCE", options.ocrMode());
        assertEquals(Boolean.TRUE, options.mathVisionCorrection());
        assertEquals("MATH_TEXTBOOK", options.resolvedDocumentProfile());
        assertEquals("v1", options.documentProfileVersion());
    }

    @Test
    void keepsAutoProfileStrategyUnspecifiedForRuntimeSelection() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                null, null, null, null,
                "AUTO", null, null);

        assertEquals(null, options.chunkingStrategy());
        assertEquals(1200, options.chunkMaxSize());
        assertEquals(150, options.chunkOverlap());
        assertEquals("CHARACTER", options.chunkUnit());
        assertEquals("AUTO", options.requestedDocumentProfile());
        assertEquals("GENERAL_DOCUMENT", options.resolvedDocumentProfile());
    }

    @Test
    void explicitOptionsOverrideProfileDefaults() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true, false, false,
                "fixed-size", 900, 90, "token",
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                false, "jpn+eng", "DISABLED", false,
                "MATH_TEXTBOOK", null, null);

        assertEquals("fixed-size", options.chunkingStrategy());
        assertEquals(900, options.chunkMaxSize());
        assertEquals("TOKEN", options.chunkUnit());
        assertEquals(Boolean.FALSE, options.ocrRequired());
        assertEquals("jpn+eng", options.ocrLanguage());
        assertEquals("DISABLED", options.ocrMode());
        assertEquals(Boolean.FALSE, options.mathVisionCorrection());
    }

    @Test
    void explicitOcrDisableKeepsProfilePlanConsistent() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                false, null, null, null,
                "MATH_TEXTBOOK", null, null);

        assertEquals(Boolean.FALSE, options.ocrRequired());
        assertEquals("DISABLED", options.ocrMode());
    }

    @Test
    void rejectsContradictoryOcrOverrides() {
        assertThrows(IllegalArgumentException.class, () -> new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                false, null, "FORCE", null,
                "MATH_TEXTBOOK", null, null));
    }

    @Test
    void acceptsLegacyOcrRequiredFalseWithAutoMode() {
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                false, null, "AUTO", null,
                "PROFESSIONAL_BOOK", null, null);

        assertEquals(Boolean.FALSE, options.ocrRequired());
        assertEquals("AUTO", options.ocrMode());
    }

    @Test
    void keepsLegacyOptionsJsonFreeOfProfileFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        String legacyJson = objectMapper.writeValueAsString(MarkdownPipelineOptions.none());
        String profileJson = objectMapper.writeValueAsString(new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                null, null, null, null,
                "TEXTBOOK", null, null));

        assertFalse(legacyJson.contains("DocumentProfile"));
        assertTrue(profileJson.contains("requestedDocumentProfile"));
        assertTrue(profileJson.contains("resolvedDocumentProfile"));
    }

    @Test
    void rejectsUnknownDocumentProfile() {
        assertThrows(IllegalArgumentException.class, () -> new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                null, null, null, null,
                "UNKNOWN_PROFILE", null, null));
    }
}
