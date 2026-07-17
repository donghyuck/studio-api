package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.LinkedHashMap;
import java.util.Map;

public record PdfDocumentAnalysis(
        int pageCount,
        int sampledPages,
        double textDensity,
        double imageDensity,
        double tableDensity,
        boolean ocrRecommended,
        double mojibakeScore,
        double hangulRatio,
        double mathSignalScore,
        PdfDocumentKind documentKind) {

    public PdfDocumentAnalysis {
        pageCount = Math.max(0, pageCount);
        sampledPages = Math.max(0, sampledPages);
        textDensity = bounded(textDensity);
        imageDensity = bounded(imageDensity);
        tableDensity = bounded(tableDensity);
        mojibakeScore = bounded(mojibakeScore);
        hangulRatio = bounded(hangulRatio);
        mathSignalScore = bounded(mathSignalScore);
        documentKind = documentKind == null ? PdfDocumentKind.GENERAL : documentKind;
    }

    public static PdfDocumentAnalysis unknown(Integer pageCount) {
        return new PdfDocumentAnalysis(
                pageCount == null ? 0 : pageCount,
                0,
                0.0d,
                0.0d,
                0.0d,
                false,
                0.0d,
                0.0d,
                0.0d,
                PdfDocumentKind.GENERAL);
    }

    public boolean mathLike() {
        return documentKind == PdfDocumentKind.MATH_LIKE
                || documentKind == PdfDocumentKind.MIXED
                || mathSignalScore >= 0.5d;
    }

    public Map<String, Object> metadata() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("pageCount", pageCount);
        value.put("sampledPages", sampledPages);
        value.put("textDensity", textDensity);
        value.put("imageDensity", imageDensity);
        value.put("tableDensity", tableDensity);
        value.put("ocrRecommended", ocrRecommended);
        value.put("mojibakeScore", mojibakeScore);
        value.put("hangulRatio", hangulRatio);
        value.put("mathSignalScore", mathSignalScore);
        value.put("documentKind", documentKind.name());
        return value;
    }

    private static double bounded(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
