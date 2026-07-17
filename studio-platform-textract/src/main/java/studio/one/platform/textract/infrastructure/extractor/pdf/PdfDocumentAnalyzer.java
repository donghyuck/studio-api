package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfDocumentAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentAnalyzer.class);
    private static final int DEFAULT_SAMPLE_PAGES = 5;
    private static final double SCANNED_TEXT_DENSITY_THRESHOLD = 0.03d;
    private static final double MOJIBAKE_THRESHOLD = 0.02d;
    private static final double MATH_SIGNAL_THRESHOLD = 0.08d;

    public PdfDocumentAnalysis analyze(PdfExtractionRequest request) {
        if (request == null || request.bytes().length == 0) {
            return PdfDocumentAnalysis.unknown(null);
        }
        try (PDDocument document = Loader.loadPDF(request.bytes())) {
            int pageCount = document.getNumberOfPages();
            int samplePages = Math.min(Math.max(1, pageCount), DEFAULT_SAMPLE_PAGES);
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= samplePages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                text.append(stripper.getText(document)).append('\n');
            }
            return analyzeText(pageCount, samplePages, text.toString(), request.filename());
        } catch (IOException | RuntimeException ex) {
            log.debug("Failed to analyze PDF before engine selection.", ex);
            return PdfDocumentAnalysis.unknown(request.options().pageCount());
        }
    }

    PdfDocumentAnalysis analyzeText(int pageCount, int samplePages, String text, String filename) {
        String content = text == null ? "" : text;
        int length = content.strip().length();
        double textDensity = ratio(length, Math.max(1, samplePages) * 2_000);
        double mojibakeScore = ratio(countMojibake(content), Math.max(1, length));
        double hangulRatio = ratio(countHangul(content), Math.max(1, countLetters(content)));
        double mathSignalScore = mathSignalScore(content, filename);
        double tableDensity = tableDensity(content);
        boolean scanned = textDensity < SCANNED_TEXT_DENSITY_THRESHOLD;
        boolean ocrRecommended = scanned || mojibakeScore >= MOJIBAKE_THRESHOLD;
        boolean mathLike = mathSignalScore >= MATH_SIGNAL_THRESHOLD || mathFilename(filename);
        PdfDocumentKind kind;
        if (mathLike && ocrRecommended) {
            kind = PdfDocumentKind.MIXED;
        } else if (mathLike) {
            kind = PdfDocumentKind.MATH_LIKE;
        } else if (scanned) {
            kind = PdfDocumentKind.SCANNED;
        } else {
            kind = PdfDocumentKind.GENERAL;
        }
        double imageDensity = scanned ? 1.0d : 0.0d;
        return new PdfDocumentAnalysis(pageCount, samplePages, textDensity, imageDensity, tableDensity,
                ocrRecommended, mojibakeScore, hangulRatio, mathSignalScore, kind);
    }

    private double mathSignalScore(String text, String filename) {
        int length = Math.max(1, text == null ? 0 : text.length());
        int score = 0;
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if ("=+-*/^√∑∫≤≥≠∞πθαβγΔ".indexOf(ch) >= 0) {
                    score++;
                }
            }
            score += occurrences(text, "log") + occurrences(text, "sin") + occurrences(text, "cos");
        }
        if (mathFilename(filename)) {
            score += 20;
        }
        return ratio(score, Math.max(20, length / 20));
    }

    private double tableDensity(String text) {
        if (text == null || text.isBlank()) {
            return 0.0d;
        }
        long lineCount = text.lines().count();
        if (lineCount == 0) {
            return 0.0d;
        }
        long tableLines = text.lines()
                .filter(line -> line.contains("|") || line.matches(".*\\S\\s{2,}\\S.*"))
                .count();
        return ratio((int) tableLines, (int) lineCount);
    }

    private boolean mathFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(filename, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.contains("math")
                || normalized.contains("algebra")
                || normalized.contains("calculus")
                || normalized.contains("수학")
                || normalized.contains("미적분")
                || normalized.contains("기하");
    }

    private int countMojibake(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\uFFFD' || ch == '□' || ch == '�') {
                count++;
            }
        }
        return count;
    }

    private int countHangul(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= '\uAC00' && ch <= '\uD7A3') || (ch >= '\u3130' && ch <= '\u318F')) {
                count++;
            }
        }
        return count;
    }

    private int countLetters(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private int occurrences(String text, String token) {
        int count = 0;
        int index = 0;
        String lower = text.toLowerCase(Locale.ROOT);
        while ((index = lower.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0.0d : Math.max(0.0d, Math.min(1.0d, (double) numerator / denominator));
    }
}
