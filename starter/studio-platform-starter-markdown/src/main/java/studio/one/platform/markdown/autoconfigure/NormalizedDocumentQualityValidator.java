package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

public class NormalizedDocumentQualityValidator {

    public List<String> validate(NormalizedDocument document, String markdown) {
        List<String> issues = new ArrayList<>();
        if (document == null || document.blocks().isEmpty()) {
            issues.add("NO_NORMALIZED_BLOCKS");
        }
        if (markdown == null || markdown.isBlank()) {
            issues.add("MARKDOWN_BLANK");
        }
        if (document == null) {
            return issues;
        }
        Map<String, Object> metadata = document.metadata();
        Map<?, ?> analysis = map(metadata.get("pdfAnalysis"));
        boolean effectiveOcrApplied = booleanValue(metadata.get("ocrApplied"))
                || booleanValue(metadata.get("koreanTextOcrApplied"));
        if (booleanValue(analysis.get("ocrRecommended")) && !effectiveOcrApplied) {
            issues.add("OCR_RECOMMENDED_BUT_NOT_APPLIED");
        }
        if (number(analysis.get("mojibakeScore")) >= 0.02d || mojibakeRatio(markdown) >= 0.02d) {
            issues.add("TEXT_POSSIBLY_GARBLED");
        }
        if (jamoLineRatio(markdown) >= 0.005d) {
            issues.add("KOREAN_JAMO_REVIEW_REQUIRED");
        }
        if (metadata.get("koreanTextOcrRequestedPages") instanceof List<?> requestedPages
                && !requestedPages.isEmpty()
                && !booleanValue(metadata.get("koreanTextOcrComplete"))) {
            issues.add("KOREAN_TEXT_OCR_INCOMPLETE");
        }
        if (koreanDocument(document, metadata)
                && letterCount(markdown) >= 100
                && hangulLetterRatio(markdown) < 0.05d
                && asciiLatinLetterRatio(markdown) >= 0.50d) {
            issues.add("KOREAN_TEXT_GARBLING");
        }
        if (booleanValue(metadata.get("fallbackApplied"))
                || "pymupdf4llm".equals(text(metadata.get("fallbackFrom")))) {
            issues.add("PYMUPDF_FALLBACK_USED");
        }
        if (number(analysis.get("textDensity")) > 0.0d && number(analysis.get("textDensity")) < 0.03d) {
            issues.add("LOW_TEXT_DENSITY");
        }
        if ("MATH_DOCUMENT".equals(text(metadata.get("pdfRecommendedRoute")))
                && !"MATH_DOCUMENT".equals(text(metadata.get("pdfActualRoute")))) {
            issues.add("MATH_DOCUMENT_REVIEW_REQUIRED");
        }
        if (mathDocument(metadata, analysis) && !hasMarkdownMath(markdown)) {
            issues.add("MATH_NOT_RENDERED_AS_MARKDOWN");
        }
        if (mathDocument(metadata, analysis) && mathRenderingRetention(document.blocks(), markdown) < 0.10d) {
            issues.add("MATH_RENDERING_LOSS");
        }
        if (mathDocument(metadata, analysis) && booleanValue(metadata.get("mathMarkdownApplied"))
                && lacksStructuredLatex(markdown)) {
            issues.add("MATH_MARKDOWN_HEURISTIC_ONLY");
        }
        boolean ocrDocument = effectiveOcrApplied
                || "pymupdf-ocr-dict".equals(text(metadata.get("blockSource")));
        if (ocrDocument && ocrOnlyRatio(document.blocks()) >= 0.9d) {
            issues.add("OCR_BLOCKS_NOT_RECLASSIFIED");
        }
        if (ocrDocument && ocrNoiseLineRatio(markdown) >= 0.08d) {
            issues.add("OCR_NOISE_REVIEW_REQUIRED");
        }
        if (ocrDocument && shortLineRatio(markdown, 5) >= 0.18d) {
            issues.add("FRAGMENTED_SHORT_LINES");
        }
        if (ocrDocument && brokenLatex(markdown)) {
            issues.add("BROKEN_LATEX_DELIMITER");
        }
        if (ocrDocument && suspiciousMathLineRatio(markdown) >= 0.05d) {
            issues.add("MATH_OCR_REVIEW_REQUIRED");
        }
        if (ocrDocument && longKoreanNoSpaceLineRatio(markdown) >= 0.04d) {
            issues.add("KOREAN_SPACING_REVIEW_REQUIRED");
        }
        if (pdfOrNativeExtraction(metadata) && missingProvenanceRatio(document.blocks()) > 0.25d) {
            issues.add("BLOCK_PROVENANCE_INCOMPLETE");
        }
        if (pdfOrNativeExtraction(metadata) && missingPageRatio(document.blocks()) > 0.0d) {
            issues.add("PAGE_PROVENANCE_INCOMPLETE");
        }
        if (pdfOrNativeExtraction(metadata) && pageSearchableRatio(document.blocks()) < 0.90d) {
            issues.add("PAGE_SEARCHABILITY_REVIEW_REQUIRED");
        }
        if (mathDocument(metadata, analysis) && mathPageCoverageRatio(document.blocks()) < 0.90d) {
            issues.add("MATH_PAGE_PROVENANCE_REVIEW_REQUIRED");
        }
        if (pageQualityReviewRequired(metadata)) {
            issues.add("PAGE_QUALITY_REVIEW_REQUIRED");
        }
        if (pageCoverageRatio(document.blocks(), analysis) < 0.95d) {
            issues.add("CONTENT_PAGE_COVERAGE_INCOMPLETE");
        }
        return issues.stream().distinct().toList();
    }

    private boolean koreanDocument(NormalizedDocument document, Map<String, Object> metadata) {
        String language = text(metadata.get("ocrLanguage")).toLowerCase(java.util.Locale.ROOT);
        String filename = document.filename() == null ? "" : document.filename();
        return language.contains("kor") || filename.codePoints().anyMatch(ch -> (ch >= 0xAC00 && ch <= 0xD7A3)
                || (ch >= 0x1100 && ch <= 0x11FF) || (ch >= 0x3131 && ch <= 0x318E));
    }

    private long letterCount(String text) {
        return text == null ? 0L : text.codePoints().filter(Character::isLetter).count();
    }

    private double hangulLetterRatio(String text) {
        long letters = letterCount(text);
        if (letters == 0) {
            return 0.0d;
        }
        long hangul = text.codePoints().filter(ch -> ch >= 0xAC00 && ch <= 0xD7A3).count();
        return (double) hangul / letters;
    }

    private double asciiLatinLetterRatio(String text) {
        long letters = letterCount(text);
        if (letters == 0) {
            return 0.0d;
        }
        long latin = text.codePoints().filter(ch -> (ch >= 'A' && ch <= 'Z')
                || (ch >= 'a' && ch <= 'z')).count();
        return (double) latin / letters;
    }

    private double jamoLineRatio(String markdown) {
        List<String> lines = nonBlankLines(markdown);
        if (lines.isEmpty()) {
            return 0.0d;
        }
        long affected = lines.stream().filter(OcrTextNormalizer::hasSuspiciousJamo).count();
        return (double) affected / lines.size();
    }

    private double mathRenderingRetention(List<NormalizedBlock> blocks, String markdown) {
        long mathBlocks = blocks.stream().filter(NormalizedBlock::hasText)
                .filter(block -> looksLikeMathText(block.text())).count();
        if (mathBlocks < 20) {
            return 1.0d;
        }
        long rendered = nonBlankLines(markdown).stream().filter(this::renderedMathLine).count();
        return (double) rendered / mathBlocks;
    }

    private boolean renderedMathLine(String line) {
        return line.contains("$") || line.contains("\\frac") || line.contains("\\sqrt")
                || line.contains("\\[") || line.contains("\\(");
    }

    private double pageCoverageRatio(List<NormalizedBlock> blocks, Map<?, ?> analysis) {
        int expected = (int) number(analysis.get("pageCount"));
        if (expected <= 0) {
            return 1.0d;
        }
        long actual = blocks.stream()
                .map(block -> block.page() == null ? pageFromSourceRef(block.sourceRef()) : block.page())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        return (double) actual / expected;
    }

    private Integer pageFromSourceRef(String sourceRef) {
        if (sourceRef == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("page\\[(\\d+)]").matcher(sourceRef);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private boolean pageQualityReviewRequired(Map<String, Object> metadata) {
        Object value = metadata.get("pageQuality");
        if (!(value instanceof List<?> pages)) {
            return false;
        }
        return pages.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(page -> "REVIEW_REQUIRED".equals(text(page.get("status"))));
    }

    private double shortLineRatio(String markdown, int maxLength) {
        List<String> lines = nonBlankLines(markdown);
        if (lines.isEmpty()) {
            return 0.0d;
        }
        long shortLines = lines.stream()
                .filter(line -> line.length() <= maxLength)
                .count();
        return (double) shortLines / lines.size();
    }

    private boolean brokenLatex(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        long dollars = markdown.chars().filter(ch -> ch == '$').count();
        return dollars % 2 != 0
                || markdown.contains("\"$")
                || markdown.contains("$\"")
                || markdown.contains("@$");
    }

    private double ocrOnlyRatio(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> contentBlocks = blocks.stream()
                .filter(NormalizedBlock::hasText)
                .toList();
        if (contentBlocks.isEmpty()) {
            return 0.0d;
        }
        long ocr = contentBlocks.stream()
                .filter(block -> block.type() == NormalizedBlockType.OCR_TEXT)
                .count();
        return (double) ocr / contentBlocks.size();
    }

    private boolean lacksStructuredLatex(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return true;
        }
        return !markdown.contains("\\frac")
                && !markdown.contains("\\sqrt")
                && !markdown.contains("\\begin{")
                && !markdown.contains("\\le")
                && !markdown.contains("\\ge");
    }

    private double ocrNoiseLineRatio(String markdown) {
        List<String> lines = nonBlankLines(markdown);
        if (lines.isEmpty()) {
            return 0.0d;
        }
        long noisy = lines.stream()
                .filter(this::looksLikeOcrNoise)
                .count();
        return (double) noisy / lines.size();
    }

    private double suspiciousMathLineRatio(String markdown) {
        List<String> lines = nonBlankLines(markdown);
        if (lines.isEmpty()) {
            return 0.0d;
        }
        long suspicious = lines.stream()
                .filter(line -> line.matches(".*[0-9A-Za-z][?°][0-9A-Za-z]?.*")
                        || line.contains("--")
                        || line.contains("~B")
                        || line.contains("22°"))
                .count();
        return (double) suspicious / lines.size();
    }

    private double longKoreanNoSpaceLineRatio(String markdown) {
        List<String> lines = nonBlankLines(markdown);
        if (lines.isEmpty()) {
            return 0.0d;
        }
        long longNoSpace = lines.stream()
                .filter(line -> line.matches(".*[가-힣]{8,}.*"))
                .count();
        return (double) longNoSpace / lines.size();
    }

    private boolean pdfOrNativeExtraction(Map<String, Object> metadata) {
        return metadata.containsKey("pdfAnalysis")
                || metadata.containsKey("pdfExtractionEngine")
                || metadata.containsKey("extractionEngine");
    }

    private boolean looksLikeOcrNoise(String line) {
        String text = line == null ? "" : line.trim();
        if (text.length() < 3 || text.length() > 40) {
            return false;
        }
        boolean hasHangul = text.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
        boolean hasMath = text.chars().anyMatch(ch -> "0123456789=+-*/^".indexOf(ch) >= 0);
        if (hasHangul || hasMath) {
            return false;
        }
        long letters = text.chars().filter(Character::isLetter).count();
        return letters >= 3 && text.matches("[A-Za-z\\s!|/\\\\.,:;~_-]+");
    }

    private List<String> nonBlankLines(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        return markdown.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private double missingProvenanceRatio(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> contentBlocks = blocks.stream()
                .filter(NormalizedBlock::hasText)
                .toList();
        if (contentBlocks.isEmpty()) {
            return 0.0d;
        }
        long missing = contentBlocks.stream()
                .filter(block -> !hasProvenance(block))
                .count();
        return (double) missing / contentBlocks.size();
    }

    private double missingPageRatio(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> contentBlocks = blocks.stream()
                .filter(NormalizedBlock::hasText)
                .toList();
        if (contentBlocks.isEmpty()) {
            return 0.0d;
        }
        long missing = contentBlocks.stream()
                .filter(block -> block.page() == null && !sourceRefHasPage(block.sourceRef())
                        && !sourceRefHasPage(text(block.metadata().get("sourceRef"))))
                .count();
        return (double) missing / contentBlocks.size();
    }

    private double pageSearchableRatio(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> contentBlocks = blocks.stream()
                .filter(NormalizedBlock::hasText)
                .filter(block -> block.text().trim().length() >= 8 || looksLikeMathText(block.text()))
                .toList();
        if (contentBlocks.isEmpty()) {
            return 0.0d;
        }
        long searchable = contentBlocks.stream()
                .filter(this::hasPageProvenance)
                .count();
        return (double) searchable / contentBlocks.size();
    }

    private double mathPageCoverageRatio(List<NormalizedBlock> blocks) {
        List<NormalizedBlock> mathBlocks = blocks.stream()
                .filter(NormalizedBlock::hasText)
                .filter(block -> looksLikeMathText(block.text()))
                .toList();
        if (mathBlocks.isEmpty()) {
            return 1.0d;
        }
        long covered = mathBlocks.stream()
                .filter(this::hasPageProvenance)
                .count();
        return (double) covered / mathBlocks.size();
    }

    private boolean hasPageProvenance(NormalizedBlock block) {
        return block.page() != null
                || sourceRefHasPage(block.sourceRef())
                || sourceRefHasPage(text(block.metadata().get("sourceRef")))
                || positiveInteger(block.metadata().get("pageFrom"))
                || positiveInteger(block.metadata().get("page"));
    }

    private boolean sourceRefHasPage(String sourceRef) {
        return sourceRef != null && sourceRef.matches(".*page\\[\\d+].*");
    }

    private boolean positiveInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue() > 0;
        }
        if (value == null) {
            return false;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim()) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean looksLikeMathText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("$")
                || text.contains("\\frac")
                || text.contains("\\sqrt")
                || text.matches(".*[0-9A-Za-z가-힣][=+\\-*/^][0-9A-Za-z가-힣({\\[].*")
                || text.matches(".*(^|[^A-Za-z가-힣])[xyab]\\s*(\\^\\s*\\d+|[=+\\-*/]).*")
                || text.matches(".*\\d\\s*[xyab](\\s*\\^\\s*\\d+)?.*");
    }

    private boolean hasProvenance(NormalizedBlock block) {
        return notBlank(block.sourceRef())
                || block.page() != null
                || block.slide() != null
                || !block.blockIds().isEmpty()
                || block.metadata().containsKey("startOffset")
                || block.metadata().containsKey("endOffset")
                || block.metadata().containsKey("bbox");
    }

    private double mojibakeRatio(String text) {
        if (text == null || text.isBlank()) {
            return 0.0d;
        }
        int bad = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\uFFFD' || ch == '□' || ch == '�') {
                bad++;
            }
        }
        return (double) bad / text.length();
    }

    private boolean mathDocument(Map<String, Object> metadata, Map<?, ?> analysis) {
        String route = text(metadata.get("pdfRecommendedRoute"));
        String kind = text(analysis.get("documentKind"));
        return "MATH_DOCUMENT".equals(route) || "MATH_LIKE".equals(kind) || "MIXED".equals(kind);
    }

    private boolean hasMarkdownMath(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        int dollars = markdown.length() - markdown.replace("$", "").length();
        return dollars >= 2
                || markdown.contains("\\(")
                || markdown.contains("\\[")
                || markdown.contains("\\frac")
                || markdown.contains("\\sqrt")
                || markdown.contains("\\begin{");
    }

    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    private double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0.0d;
            }
        }
        return 0.0d;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
