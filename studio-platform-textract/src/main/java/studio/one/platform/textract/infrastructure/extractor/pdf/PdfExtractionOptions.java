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
        boolean includeImages,
        String ocrLanguage,
        String ocrMode,
        boolean mathVisionCorrection) {

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
        ocrLanguage = normalize(ocrLanguage);
        ocrMode = normalizeOcrMode(ocrMode);
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
            int pyMuPdf4LlmMaxFileSizeBytes,
            boolean largePdfEnabled,
            int largePdfPageThreshold,
            int largePdfBatchSize,
            boolean continueOnPartFailure,
            int maxPartFailures,
            Integer pageFrom,
            Integer pageTo,
            Integer maxPages,
            boolean includeImages,
            String ocrLanguage,
            String ocrMode) {
        this(engine, fallbackEnabled, pdfBoxEnabled, pyMuPdf4LlmEnabled, ocrRequired, preserveLayout,
                tableExtractionRequired, pageCount, preferPyMuPdf4LlmMinPages, pyMuPdf4LlmMaxFileSizeBytes,
                largePdfEnabled, largePdfPageThreshold, largePdfBatchSize, continueOnPartFailure, maxPartFailures,
                pageFrom, pageTo, maxPages, includeImages, ocrLanguage, ocrMode, false);
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
                false,
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
                false,
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
                largePdfEnabled,
                largePdfPageThreshold,
                largePdfBatchSize,
                continueOnPartFailure,
                maxPartFailures,
                pageFrom,
                pageTo,
                maxPages,
                includeImages,
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
                includeImages,
                ocrLanguage,
                ocrMode,
                mathVisionCorrection);
    }

    public PdfExtractionOptions withOcrRequired(boolean ocrRequired) {
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
                includeImages,
                ocrLanguage,
                ocrMode,
                mathVisionCorrection);
    }

    public PdfExtractionOptions withOcrLanguage(String ocrLanguage) {
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
                includeImages,
                ocrLanguage,
                ocrMode,
                mathVisionCorrection);
    }

    public PdfExtractionOptions withOcrMode(String ocrMode) {
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
                includeImages,
                ocrLanguage,
                ocrMode,
                mathVisionCorrection);
    }

    public PdfExtractionOptions withMathVisionCorrection(boolean mathVisionCorrection) {
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
                includeImages,
                ocrLanguage,
                ocrMode,
                mathVisionCorrection);
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
                includeImages,
                ocrLanguage,
                ocrMode,
                mathVisionCorrection);
    }

    public boolean rangeRequested() {
        return pageFrom != null || pageTo != null || maxPages != null;
    }

    public boolean prefersPyMuPdf4Llm() {
        return ocrForceRequested()
                || preserveLayout
                || tableExtractionRequired
                || (pageCount != null
                        && preferPyMuPdf4LlmMinPages > 0
                        && pageCount >= preferPyMuPdf4LlmMinPages);
    }

    public boolean ocrForceRequested() {
        return !ocrDisabled() && (ocrRequired || "FORCE".equals(ocrMode));
    }

    public boolean ocrDisabled() {
        return "DISABLED".equals(ocrMode);
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeOcrMode(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "AUTO";
        }
        normalized = normalized.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        if (!normalized.equals("AUTO") && !normalized.equals("FORCE") && !normalized.equals("DISABLED")) {
            throw new IllegalArgumentException("Unsupported ocrMode: " + value);
        }
        return normalized;
    }
}
