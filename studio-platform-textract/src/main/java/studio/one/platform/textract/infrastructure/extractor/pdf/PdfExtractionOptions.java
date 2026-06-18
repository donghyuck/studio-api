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
        int pyMuPdf4LlmMaxFileSizeBytes,
        boolean largePdfEnabled,
        int largePdfPageThreshold,
        int largePdfBatchSize,
        boolean continueOnPartFailure,
        int maxPartFailures,
        Integer pageFrom,
        Integer pageTo,
        Integer maxPages,
        boolean includeImages) {

    private static final int DEFAULT_PYMUPDF_MAX_BYTES = 50 * 1024 * 1024;
    private static final int DEFAULT_LARGE_PDF_PAGE_THRESHOLD = 100;
    private static final int DEFAULT_LARGE_PDF_BATCH_SIZE = 50;

    public PdfExtractionOptions {
        engine = engine == null ? PdfExtractionMode.AUTO : engine;
        if (pageCount != null && pageCount < 0) {
            pageCount = null;
        }
        preferPyMuPdf4LlmMinPages = Math.max(0, preferPyMuPdf4LlmMinPages);
        pyMuPdf4LlmMaxFileSizeBytes = pyMuPdf4LlmMaxFileSizeBytes <= 0
                ? DEFAULT_PYMUPDF_MAX_BYTES
                : pyMuPdf4LlmMaxFileSizeBytes;
        largePdfPageThreshold = largePdfPageThreshold <= 0
                ? DEFAULT_LARGE_PDF_PAGE_THRESHOLD
                : largePdfPageThreshold;
        largePdfBatchSize = largePdfBatchSize <= 0
                ? DEFAULT_LARGE_PDF_BATCH_SIZE
                : largePdfBatchSize;
        pageFrom = positiveOrNull(pageFrom);
        pageTo = positiveOrNull(pageTo);
        maxPages = positiveOrNull(maxPages);
        if (pageFrom != null && pageTo != null && pageTo < pageFrom) {
            int originalFrom = pageFrom;
            pageFrom = pageTo;
            pageTo = originalFrom;
        }
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
                DEFAULT_PYMUPDF_MAX_BYTES,
                true,
                DEFAULT_LARGE_PDF_PAGE_THRESHOLD,
                DEFAULT_LARGE_PDF_BATCH_SIZE,
                true,
                0,
                null,
                null,
                null,
                false);
    }

    public PdfExtractionOptions(
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
        this(
                engine,
                fallbackEnabled,
                pdfBoxEnabled,
                pyMuPdf4LlmEnabled,
                ocrRequired,
                preserveLayout,
                tableExtractionRequired,
                pageCount,
                preferPyMuPdf4LlmMinPages,
                pyMuPdf4LlmMaxFileSizeBytes,
                true,
                DEFAULT_LARGE_PDF_PAGE_THRESHOLD,
                DEFAULT_LARGE_PDF_BATCH_SIZE,
                true,
                0,
                null,
                null,
                null,
                false);
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
                pyMuPdf4LlmMaxFileSizeBytes,
                largePdfEnabled,
                largePdfPageThreshold,
                largePdfBatchSize,
                continueOnPartFailure,
                maxPartFailures,
                pageFrom,
                pageTo,
                maxPages,
                includeImages);
    }

    public PdfExtractionOptions forPageRange(int pageFrom, int pageTo) {
        int count = Math.max(0, pageTo - pageFrom + 1);
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
                pyMuPdf4LlmMaxFileSizeBytes,
                largePdfEnabled,
                largePdfPageThreshold,
                largePdfBatchSize,
                continueOnPartFailure,
                maxPartFailures,
                pageFrom,
                pageTo,
                count,
                includeImages);
    }

    public boolean rangeRequested() {
        return pageFrom != null || pageTo != null || maxPages != null;
    }

    public boolean prefersPyMuPdf4Llm() {
        return ocrRequired
                || preserveLayout
                || tableExtractionRequired
                || (pageCount != null
                        && preferPyMuPdf4LlmMinPages > 0
                        && pageCount >= preferPyMuPdf4LlmMinPages);
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }
}
