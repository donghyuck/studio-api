package studio.one.platform.textract.infrastructure.extractor.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

class PdfExtractionEngineSelectorTest {

    @Test
    void autoUsesPdfBoxWhenWorkerIsDisabled() {
        ParsedFile result = selector(pdfBox("pdfbox text")).extract(request(PdfExtractionOptions.defaults()));

        assertThat(result.plainText()).isEqualTo("pdfbox text");
        assertThat(result.metadata()).containsEntry(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pdfbox");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void explicitPyMuPdfFallsBackToPdfBoxWhenFallbackIsEnabled() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PYMUPDF4LLM,
                true,
                true,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);

        ParsedFile result = selector(failingPyMuPdf(), pdfBox("fallback text")).extract(request(options));

        assertThat(result.plainText()).isEqualTo("fallback text");
        assertThat(result.metadata()).containsEntry(PdfExtractionEngineSelector.KEY_FALLBACK_FROM, "pymupdf4llm");
        assertThat(result.warnings()).extracting(warning -> warning.canonicalCode())
                .contains("PYMUPDF4LLM_FAILED");
    }

    @Test
    void explicitPyMuPdfPropagatesFailureWhenFallbackIsDisabled() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PYMUPDF4LLM,
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);

        assertThatThrownBy(() -> selector(failingPyMuPdf(), pdfBox("fallback text")).extract(request(options)))
                .isInstanceOf(FileParseException.class)
                .hasMessageContaining("worker failed");
    }

    @Test
    void autoPrefersPyMuPdfWhenPageCountMatchesThreshold() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.AUTO,
                true,
                true,
                true,
                false,
                false,
                false,
                3,
                3,
                1024);

        ParsedFile result = selector(pdfBox("pdfbox text"), pyMuPdf("markdown text")).extract(request(options));

        assertThat(result.plainText()).isEqualTo("markdown text");
        assertThat(result.metadata()).containsEntry(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm");
    }

    @Test
    void autoUsesPyMuPdfWhenPdfBoxIsDisabled() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.AUTO,
                true,
                false,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);

        ParsedFile result = selector(pyMuPdf("markdown text")).extract(request(options));

        assertThat(result.plainText()).isEqualTo("markdown text");
        assertThat(result.metadata()).containsEntry(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm");
    }

    @Test
    void supportsRespectsExplicitPdfBoxSelection() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.PDFBOX,
                true,
                false,
                true,
                false,
                false,
                false,
                null,
                3,
                1024);

        boolean supported = selector(pyMuPdf("markdown text")).supports(request(options));

        assertThat(supported).isFalse();
    }

    private PdfExtractionEngineSelector selector(PdfExtractionEngine... engines) {
        return new PdfExtractionEngineSelector(List.of(engines));
    }

    private PdfExtractionRequest request(PdfExtractionOptions options) {
        return new PdfExtractionRequest(new byte[] {1}, "application/pdf", "sample.pdf", options);
    }

    private PdfExtractionEngine pdfBox(String text) {
        return fixed(PdfExtractionEngineType.PDFBOX, "pdfbox", text);
    }

    private PdfExtractionEngine pyMuPdf(String text) {
        return fixed(PdfExtractionEngineType.PYMUPDF4LLM, "pymupdf4llm", text);
    }

    private PdfExtractionEngine fixed(PdfExtractionEngineType type, String engineName, String text) {
        return new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return type;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) {
                ParsedFile file = ParsedFile.textOnly(DocumentFormat.PDF, text, "sample.pdf");
                return new ParsedFile(
                        file.format(),
                        file.plainText(),
                        file.blocks(),
                        Map.of(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, engineName),
                        file.warnings(),
                        file.pages(),
                        file.tables(),
                        file.images(),
                        file.ocrApplied());
            }
        };
    }

    private PdfExtractionEngine failingPyMuPdf() {
        return new PdfExtractionEngine() {
            @Override
            public PdfExtractionEngineType type() {
                return PdfExtractionEngineType.PYMUPDF4LLM;
            }

            @Override
            public boolean supports(PdfExtractionRequest request) {
                return true;
            }

            @Override
            public ParsedFile extract(PdfExtractionRequest request) throws FileParseException {
                throw new FileParseException("worker failed");
            }
        };
    }
}
