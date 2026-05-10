package studio.one.platform.textract.infrastructure.extractor.pdf;

public record PdfExtractionOptions(
        PdfExtractionMode engine,
        boolean fallbackEnabled,
        boolean pdfBoxEnabled,
        boolean pyMuPdf4LlmEnabled,
        boolean ocrRequired,
        boolean preserveLayout,
        boolean tableExtractionRequired,
        Integer pageCount,
        int preferPyMuPdf4LlmMinPages,
        int pyMuPdf4LlmMaxFileSizeBytes) {

    private static final int DEFAULT_PYMUPDF_MAX_BYTES = 50 * 1024 * 1024;

    public PdfExtractionOptions {
        engine = engine == null ? PdfExtractionMode.AUTO : engine;
        if (pageCount != null && pageCount < 0) {
            pageCount = null;
        }
        preferPyMuPdf4LlmMinPages = Math.max(0, preferPyMuPdf4LlmMinPages);
        pyMuPdf4LlmMaxFileSizeBytes = pyMuPdf4LlmMaxFileSizeBytes <= 0
                ? DEFAULT_PYMUPDF_MAX_BYTES
                : pyMuPdf4LlmMaxFileSizeBytes;
    }

    public static PdfExtractionOptions defaults() {
        return new PdfExtractionOptions(
                PdfExtractionMode.AUTO,
                true,
                true,
                false,
                false,
                false,
                false,
                null,
                3,
                DEFAULT_PYMUPDF_MAX_BYTES);
    }

    public PdfExtractionOptions withPageCount(Integer pageCount) {
        return new PdfExtractionOptions(
                engine,
                fallbackEnabled,
                pdfBoxEnabled,
                pyMuPdf4LlmEnabled,
                ocrRequired,
                preserveLayout,
                tableExtractionRequired,
                pageCount,
                preferPyMuPdf4LlmMinPages,
                pyMuPdf4LlmMaxFileSizeBytes);
    }

    public boolean prefersPyMuPdf4Llm() {
        return ocrRequired
                || preserveLayout
                || tableExtractionRequired
                || (pageCount != null
                        && preferPyMuPdf4LlmMinPages > 0
                        && pageCount >= preferPyMuPdf4LlmMinPages);
    }
}
