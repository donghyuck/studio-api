package studio.one.platform.textract.infrastructure.extractor.pdf.pdfbox;

public record PdfOcrFallbackOptions(
        boolean enabled,
        int maxPages,
        float dpi,
        String tesseractDataPath,
        String language) {

    private static final int DEFAULT_MAX_PAGES = 20;
    private static final float DEFAULT_DPI = 180f;

    public PdfOcrFallbackOptions {
        maxPages = maxPages <= 0 ? DEFAULT_MAX_PAGES : maxPages;
        dpi = dpi <= 0 ? DEFAULT_DPI : dpi;
        tesseractDataPath = tesseractDataPath == null ? "" : tesseractDataPath.trim();
        language = language == null || language.isBlank() ? "eng" : language.trim();
    }

    public static PdfOcrFallbackOptions disabled() {
        return new PdfOcrFallbackOptions(false, DEFAULT_MAX_PAGES, DEFAULT_DPI, "", "eng");
    }
}
