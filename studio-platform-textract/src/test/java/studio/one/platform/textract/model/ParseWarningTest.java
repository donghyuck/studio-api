package studio.one.platform.textract.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ParseWarningTest {

    @Test
    void constructorFallsBackToNormalizedCanonicalCodeAndKeepsLegacyFields() {
        ParseWarning warning = new ParseWarning("hwp.encrypted", "Encrypted HWP is not supported", "FileHeader", Map.of());

        assertEquals("hwp.encrypted", warning.code());
        assertEquals("HWP_ENCRYPTED", warning.canonicalCode());
        assertEquals(ParseWarningSeverity.WARNING, warning.severity());
        assertEquals("FileHeader", warning.path());
        assertEquals("FileHeader", warning.sourceRef());
        assertEquals("", warning.blockRef());
        assertFalse(warning.partialParse());
    }

    @Test
    void partialFactoryMarksPartialParseAndCarriesRefs() {
        ParseWarning warning = ParseWarning.partial(
                "hwp.record.eof",
                "Section parsing fell back to plain text",
                "section[0]",
                "section[0]/paragraph[1]",
                Map.of("detailCode", "SECTION_FALLBACK"));

        assertEquals("hwp.record.eof", warning.code());
        assertEquals("HWP_RECORD_EOF", warning.canonicalCode());
        assertEquals(ParseWarningSeverity.WARNING, warning.severity());
        assertEquals("section[0]", warning.path());
        assertEquals("section[0]", warning.sourceRef());
        assertEquals("section[0]/paragraph[1]", warning.blockRef());
        assertTrue(warning.partialParse());
    }

    @Test
    void warningFactoryKeepsWarningSeverityAndNonPartialState() {
        ParseWarning warning = ParseWarning.warning("hwpx.section.missing", "Missing section file", "section[0]");

        assertEquals(ParseWarningSeverity.WARNING, warning.severity());
        assertFalse(warning.partialParse());
        assertFalse(warning.isError());
    }

    @Test
    void errorFactoryMarksWarningAsError() {
        ParseWarning warning = ParseWarning.error("hwp.encrypted", "Encrypted HWP is not supported", "FileHeader", Map.of());

        assertEquals(ParseWarningSeverity.ERROR, warning.severity());
        assertFalse(warning.partialParse());
        assertTrue(warning.isError());
    }

    @Test
    void severityFallsBackToWarningForInvalidMetadataValue() {
        ParseWarning warning = new ParseWarning(
                "custom.warning",
                "Custom warning",
                "document",
                Map.of(ParseWarning.KEY_SEVERITY, "BROKEN"));

        assertEquals(ParseWarningSeverity.WARNING, warning.severity());
        assertFalse(warning.isError());
    }
}
