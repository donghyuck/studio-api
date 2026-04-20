package studio.one.platform.textract.extractor;

import java.util.Locale;

/**
 * Lightweight content type and extension based format detector.
 */
public final class DocumentFormatDetector {

    private DocumentFormatDetector() {
    }

    public static DocumentFormat detect(String contentType, String filename) {
        String type = normalize(contentType);
        String name = normalize(filename);
        if (type.startsWith("text/html") || name.endsWith(".html") || name.endsWith(".htm")) {
            return DocumentFormat.HTML;
        }
        if (type.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".csv")) {
            return DocumentFormat.TEXT;
        }
        if (type.startsWith("application/pdf") || name.endsWith(".pdf")) {
            return DocumentFormat.PDF;
        }
        if (type.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || name.endsWith(".docx")) {
            return DocumentFormat.DOCX;
        }
        if (type.startsWith("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                || name.endsWith(".pptx")) {
            return DocumentFormat.PPTX;
        }
        if (type.startsWith("image/")
                || name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".bmp")) {
            return DocumentFormat.IMAGE;
        }
        if (name.endsWith(".hwp")) {
            return DocumentFormat.HWP;
        }
        if (name.endsWith(".hwpx")) {
            return DocumentFormat.HWPX;
        }
        return DocumentFormat.UNKNOWN;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
