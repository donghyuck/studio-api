package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.NormalizedDocument;

final class RenderedMarkdownPostProcessor {
    static final double TARGET_SHORT_LINE_RATIO = 0.20d;
    static final double TARGET_QUALITY_SCORE = 0.85d;

    String postProcess(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        List<String> input = markdown.lines().toList();
        List<String> output = new ArrayList<>();
        java.util.Set<String> seenFormulaFingerprints = new java.util.LinkedHashSet<>();
        for (int i = 0; i < input.size(); i++) {
            String line = input.get(i);
            String text = line == null ? "" : line.trim();
            if (text.isBlank()) {
                appendBlank(output);
                continue;
            }
            text = normalizeFragmentLine(text);
            if (discardableLine(text)) {
                continue;
            }
            if (discardableFormulaNoise(text)) {
                continue;
            }
            if (duplicateFormulaLine(text, seenFormulaFingerprints)) {
                continue;
            }
            String next = nextNonBlank(input, i + 1);
            int previousIndex = previousContentIndex(output);
            String previous = previousIndex < 0 ? "" : output.get(previousIndex).trim();
            if (previousIndex >= 0 && attachToPrevious(previous, text, next)) {
                output.set(previousIndex, merge(previous, text));
                continue;
            }
            if (shortPrefix(text, next)) {
                output.add(merge(text, next));
                i = skipUntil(input, i + 1, next);
                continue;
            }
            output.add(text);
        }
        return trimBlankEdges(compactProseBlankLines(output));
    }

    NormalizedDocument withRenderedQuality(NormalizedDocument document, String markdown, List<String> issues) {
        if (document == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
        Map<String, Object> metrics = metrics(markdown);
        metadata.putAll(metrics);
        metadata.put("markdownQualityTargetShortLineRatio", TARGET_SHORT_LINE_RATIO);
        metadata.put("markdownQualityTargetScore", TARGET_QUALITY_SCORE);
        metadata.put("markdownShortLineCount", metrics.get("renderedMarkdownShortLineCount"));
        metadata.put("markdownShortLineRatio", metrics.get("renderedMarkdownShortLineRatio"));
        metadata.put("markdownMathBlockCount", metrics.get("renderedMarkdownFormulaLineCount"));
        List<String> qualityIssues = issues == null ? List.of() : issues.stream()
                .filter(issue -> issue != null && !issue.isBlank())
                .distinct()
                .toList();
        metadata.put("markdownQualityIssues", qualityIssues);
        metadata.put("markdownQualityStatus", qualityIssues.isEmpty() ? "VALID" : "REVIEW_REQUIRED");
        double score = qualityScore(qualityIssues, number(metrics.get("renderedMarkdownShortLineRatio")),
                number(metrics.get("renderedMarkdownJamoLineRatio")), document, markdown);
        boolean fatalQualityFailure = qualityIssues.stream().anyMatch(this::fatalIssue)
                || markdown == null
                || markdown.isBlank()
                || document.blocks().isEmpty();
        boolean ragIndexEligible = !fatalQualityFailure;
        metadata.put("markdownQualityScore", score);
        metadata.put("ragIndexEligible", ragIndexEligible);
        metadata.put("qualityGateStatus", fatalQualityFailure
                ? "BLOCKED"
                : score >= TARGET_QUALITY_SCORE && qualityIssues.isEmpty() ? "PASSED" : "REVIEW_REQUIRED");
        return NormalizedDocument.builder(document.sourceDocumentId())
                .plainText(document.plainText())
                .sourceFormat(document.sourceFormat())
                .filename(document.filename())
                .blocks(document.blocks())
                .metadata(metadata)
                .build();
    }

    Map<String, Object> metrics(String markdown) {
        List<String> lines = markdown == null || markdown.isBlank()
                ? List.of()
                : markdown.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        long shortLines = lines.stream()
                .filter(line -> line.length() <= 5)
                .filter(line -> !structural(line))
                .filter(line -> !formulaLine(line))
                .filter(line -> !shortMathLine(line))
                .filter(line -> !shortTextLabel(line))
                .count();
        long formulaLines = lines.stream().filter(this::formulaLine).count();
        long noiseLines = lines.stream().filter(this::discardableLine).count();
        long minusSuspects = lines.stream().filter(line -> line.contains("ㅡ")).count();
        long brokenDelimiter = brokenLatex(markdown) ? 1L : 0L;
        long jamoLines = lines.stream().filter(OcrTextNormalizer::hasSuspiciousJamo).count();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("renderedMarkdownNonBlankLineCount", lines.size());
        metrics.put("renderedMarkdownShortLineCount", shortLines);
        metrics.put("renderedMarkdownShortLineRatio", lines.isEmpty() ? 0.0d : (double) shortLines / lines.size());
        metrics.put("renderedMarkdownFormulaLineCount", formulaLines);
        metrics.put("renderedMarkdownNoiseLineCount", noiseLines);
        metrics.put("renderedMarkdownMinusSuspectCount", minusSuspects);
        metrics.put("renderedMarkdownBrokenLatexDelimiter", brokenDelimiter > 0);
        metrics.put("renderedMarkdownJamoLineCount", jamoLines);
        metrics.put("renderedMarkdownJamoLineRatio", lines.isEmpty() ? 0.0d : (double) jamoLines / lines.size());
        return metrics;
    }

    private void appendBlank(List<String> output) {
        if (!output.isEmpty() && !output.get(output.size() - 1).isBlank()) {
            output.add("");
        }
    }

    private List<String> compactProseBlankLines(List<String> lines) {
        List<String> compacted = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || !line.isBlank()) {
                compacted.add(line == null ? "" : line);
                continue;
            }
            String previous = previousLine(lines, index - 1);
            String next = nextLine(lines, index + 1);
            if (blankBoundary(previous, next)) {
                appendBlank(compacted);
            }
        }
        return compacted;
    }

    private String previousLine(List<String> lines, int index) {
        for (int cursor = index; cursor >= 0; cursor--) {
            String value = lines.get(cursor);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String nextLine(List<String> lines, int index) {
        for (int cursor = index; cursor < lines.size(); cursor++) {
            String value = lines.get(cursor);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean blankBoundary(String previous, String next) {
        return structural(previous) || structural(next)
                || problemHeading(previous) || problemHeading(next);
    }

    private int previousContentIndex(List<String> output) {
        for (int i = output.size() - 1; i >= 0; i--) {
            if (!output.get(i).trim().isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private boolean attachToPrevious(String previous, String current, String next) {
        if (previous.isBlank() || structural(previous) || structural(current)) {
            return false;
        }
        if (formulaLine(previous) || formulaLine(current)) {
            return formulaLine(previous) && formulaLine(current) && current.length() <= 24;
        }
        if (previous.endsWith(":") && proseLike(current)) {
            return true;
        }
        if (numberOrProblemLabel(current) && next != null && !structural(next)) {
            return false;
        }
        if (current.length() <= 5 && hasHangul(current) && hasHangul(previous)) {
            return true;
        }
        if (current.length() <= 8 && hasHangul(current) && hasHangul(previous)
                && (next == null || !hasHangul(next))) {
            return true;
        }
        if (current.matches("^\\([0-9]+\\)\\s*[가-힣]$") && hasHangul(previous)) {
            return true;
        }
        if (current.matches("^\\d$") && looksLikeMath(previous)) {
            return true;
        }
        if (current.matches("^\\d{1,4}$") && !problemNumber(current) && proseLike(previous)) {
            return true;
        }
        if (looksLikeMathFragment(current) && looksLikeMath(previous)) {
            return true;
        }
        if (current.endsWith(":") && hasHangul(current) && hasHangul(previous)) {
            return true;
        }
        return false;
    }

    private boolean shortPrefix(String current, String next) {
        if (next == null || next.isBlank() || structural(current) || structural(next)) {
            return false;
        }
        if (problemLabel(current)) {
            return true;
        }
        if (current.matches("^\\d{1,4}$") && !next.matches("^\\d{1,4}$")) {
            return true;
        }
        if (current.matches("^\\d$") && formulaLine(next)) {
            return true;
        }
        if (looksLikeMathFragment(current) && looksLikeMath(next)) {
            return true;
        }
        if (formulaLine(current) || formulaLine(next)) {
            return formulaLine(current) && formulaLine(next) && current.length() <= 24;
        }
        if (current.length() <= 5 && hasHangul(current) && hasHangul(next)) {
            return true;
        }
        if (current.endsWith(":") && hasHangul(current)) {
            return true;
        }
        return current.matches("^\\([0-9]+\\)\\s*[가-힣]$") && hasHangul(next);
    }

    private int skipUntil(List<String> input, int start, String target) {
        for (int i = start; i < input.size(); i++) {
            String text = input.get(i) == null ? "" : normalizeFragmentLine(input.get(i).trim());
            if (text.equals(target)) {
                return i;
            }
        }
        return start;
    }

    private String nextNonBlank(List<String> input, int start) {
        for (int i = start; i < input.size(); i++) {
            String text = input.get(i) == null ? "" : normalizeFragmentLine(input.get(i).trim());
            if (!text.isBlank() && !discardableLine(text)) {
                return text;
            }
        }
        return null;
    }

    private boolean discardableLine(String text) {
        if (text == null || text.isBlank() || structural(text) || formulaLine(text)) {
            return false;
        }
        String value = text.trim();
        if (value.matches("^[|°@：%+\\])»]$")) {
            return true;
        }
        if (value.matches("^[ㅜㅠㅡㅅㅎㅇㅋ]+$")) {
            return true;
        }
        if (value.matches("^\\([/\\\\|]+\\)$")) {
            return true;
        }
        if (value.matches("^\\d{5,}$")) {
            return true;
        }
        return (value.contains(" ") && OcrTextNormalizer.isDiscardableNoiseLine(value))
                || value.matches("^[=\\-_/|~.,:;!<>@：%+\\])»、…]+$")
                || value.matches("(?i)^/[\\sA-Za-z~_-]{1,8}$")
                || value.matches("(?i)^(see|wee|ee)\\s*[©®]$")
                || value.matches("(?i)^[a-z]{2,5}[\\]”’]?$")
                || value.matches("^[」'·©®、,……]+$")
                || value.matches("^\\(?[@©®]\\)?$");
    }

    private String normalizeFragmentLine(String text) {
        String value = text == null ? "" : text.trim();
        value = normalizeInlineArtifacts(value);
        if (value.matches("^\\|\\s*[^|]+$")) {
            return value.substring(1).trim();
        }
        if (value.matches("^\\|\\s*\\|\\s*[^|]+\\|$")) {
            return value.replaceFirst("^\\|\\s*\\|\\s*", "| ").trim();
        }
        return value;
    }

    private String normalizeInlineArtifacts(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return "";
        }
        value = value.replace('−', '-')
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("\\s+@\\s*$", "")
                .replaceAll("^@\\s+", "")
                .replaceAll("(?<=\\d)@\\b", "")
                .replaceAll("(?<=\\d)ㅋ(?=[+\\-*/=]?\\d)", "")
                .replaceAll("(?<=\\S)\\s*ㅡ\\s*(?=[0-9A-Za-z({\\[])\\s*", "-")
                .replaceAll("(?<=[0-9A-Za-z)])ㅡ\\s*(?=\\+\\s*[A-Za-z])", "-")
                .replaceAll("^ㅡ\\s*(?=[0-9A-Za-z({\\[])", "-")
                .replaceAll("^ㅡ\\s*(?=[\"'+\\-<>=/ㅜㅠㅅㅎ])", "-")
                .replaceAll("(?<=\\s)ㅡ\\s*(?=[\"'+\\-<>=/0-9A-Za-zㅜㅠㅅㅎ])", "-")
                .replaceAll("(?<=[=+\\-*/^({\\[])ㅡ(?=[0-9A-Za-z])", "-")
                .replaceAll("-[ㅜㅠ]+(?=\\d)", "-")
                .replaceAll("-[ㅅㅎ](?=[+\\-*/=]?\\d)", "-")
                .replaceAll("(?<=\\s)-{2,}(?=\\d)", " -")
                .replaceAll("^-{2,}(?=\\d)", "-")
                .replaceAll("\\s{2,}", " ");
        value = normalizeMixedLatinHangulNoise(value);
        if (singleUnpairedDollar(value)) {
            value = value.replace("$", "").replaceAll("\\s{2,}", " ").trim();
        }
        return value;
    }

    private String normalizeMixedLatinHangulNoise(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank() || !hasHangul(value)) {
            return value;
        }
        value = value.replaceAll("(?i)^Lecture\\s+(?=[가-힣])", "")
                .replaceAll("(?i)\\b(SSHEO|NOS|SAS|SHS|CAD|HHH|Liat!?|Leal|Lexesoll|FAIO)\\b", "")
                .replaceAll("(?i)(?<=\\p{IsHangul})(SSHEO|NOS|SAS|SHS|CAD|HHH|Liat!?|Leal|Lexesoll|FAIO)(?=\\s|\\p{IsHangul}|$)", "")
                .replaceAll("(?i)Lhe디디", "")
                .replaceAll("(?i)LSe-wlol\\]?", "")
                .replaceAll("(?i)\\bgo(?=중심)", "")
                .replaceAll("(?i)\\bF\\d{2,4}/\\s*", "")
                .replaceAll("\\s*[|/]\\s*(?=[가-힣])", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (value.matches("(?i)^[A-Za-z0-9/|\\s.,:;!~_-]{1,16}[가-힣]{1,3}$")
                && !value.matches(".*[=+\\-*/^].*")) {
            return "";
        }
        return value;
    }

    private boolean discardableFormulaNoise(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return false;
        }
        if (value.matches("^\\$\\s*[0-9A-Za-z]\\s*\\$$")
                || value.matches("^\\$\\s*[0-9A-Za-z]\\s*['’]?\\s*\\$$")) {
            return true;
        }
        if (formulaLine(value) && OcrTextNormalizer.hasSuspiciousJamo(value)
                && !value.matches(".*[가-힣].*")) {
            return true;
        }
        return value.matches("^\\$\\s*[^$]{1,2}\\s*\\$$")
                && !value.matches(".*[=+\\-*/^].*");
    }

    private boolean duplicateFormulaLine(String text, java.util.Set<String> seenFormulaFingerprints) {
        if (!formulaLine(text) || hasHangul(text)) {
            return false;
        }
        String fingerprint = formulaFingerprint(text);
        if (fingerprint.length() < 8) {
            return false;
        }
        if (seenFormulaFingerprints.contains(fingerprint)) {
            return true;
        }
        seenFormulaFingerprints.add(fingerprint);
        return false;
    }

    private String formulaFingerprint(String text) {
        String value = text == null ? "" : text.trim();
        value = value.replace('−', '-')
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("\\\\left|\\\\right", "")
                .replaceAll("\\\\mathrm\\{([^}]*)}", "$1")
                .replaceAll("\\\\text\\{([^}]*)}", "$1")
                .replaceAll("[\\s{}$]", "")
                .replaceAll("\\^\\{([^}]*)}", "^$1")
                .replaceAll("_\\{([^}]*)}", "_$1")
                .toLowerCase(java.util.Locale.ROOT);
        return value;
    }

    private String merge(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null ? "" : right.trim();
        }
        if (right == null || right.isBlank()) {
            return left.trim();
        }
        String a = left.trim();
        String b = right.trim();
        if (hasHangul(a) && hasHangul(b) && a.matches(".*[가-힣]$") && b.matches("^[가-힣].*")) {
            if (a.length() <= 3 || b.endsWith(":")) {
                return a + " " + b;
            }
            return a + b;
        }
        if (formulaLine(a) && formulaLine(b)) {
            return a + " " + b;
        }
        return a + " " + b;
    }

    private boolean structural(String text) {
        String value = text == null ? "" : text.trim();
        return value.startsWith("#")
                || value.startsWith("- ")
                || value.startsWith("* ")
                || value.startsWith("![")
                || value.startsWith("<!--")
                || markdownTableLine(value);
    }

    private boolean markdownTableLine(String value) {
        if (value == null || !value.trim().startsWith("|")) {
            return false;
        }
        long pipes = value.chars().filter(ch -> ch == '|').count();
        return pipes >= 2;
    }

    private boolean formulaLine(String text) {
        String value = text == null ? "" : text.trim();
        return value.contains("$")
                || value.contains("\\frac")
                || value.contains("\\sqrt")
                || value.contains("\\begin{");
    }

    private boolean looksLikeMath(String text) {
        String value = text == null ? "" : text.trim();
        return formulaLine(value) || value.matches(".*[0-9A-Za-z][=+\\-*/^][0-9A-Za-z({\\[].*");
    }

    private boolean looksLikeMathFragment(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("^[+\\-]?[0-9A-Za-z]+[)}]?$")
                || value.matches("^[=+\\-*/^][0-9A-Za-z]+[)}]?$")
                || value.matches("^[0-9A-Za-z]+[=+\\-*/^]$");
    }

    private boolean shortMathLine(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return false;
        }
        return value.matches("^[A-Za-z]([+\\-*/=^][A-Za-z0-9]+)+$")
                || value.matches("^[=+\\-]?[0-9A-Za-z][0-9A-Za-z+\\-*/=^'()]{1,7}$")
                || value.matches("^\\d+\\s+[+\\-]?\\d+$")
                || value.matches("^\\d+\\s+\\d+\\s+\\d+$")
                || value.matches("^\\(?\\d+[),]?$");
    }

    private boolean shortTextLabel(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank() || !hasHangul(value)) {
            return false;
        }
        return value.matches("^(개념|참고|실력\\+|하면|에서|때|이|다\\.|이므로)$")
                || value.matches("^[가-힣]{1,5}$")
                || value.matches("^[가-힣]{1,5}[.?!]$");
    }

    private boolean problemNumber(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("^\\d{3,4}$");
    }

    private boolean problemLabel(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("^(문제|유형|예제)?\\s*\\d{1,4}$")
                || value.matches("^(문제|유형|예제)\\s*\\d{1,4}.*");
    }

    private boolean problemHeading(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("^(?:문제|예제|유형)\\s*\\d{1,4}(?:\\s*[:.)-])?$");
    }

    private boolean numberOrProblemLabel(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("^\\d{1,4}$") || problemLabel(value);
    }

    private boolean proseLike(String text) {
        String value = text == null ? "" : text.trim();
        return hasHangul(value) || value.length() >= 8;
    }

    private boolean hasHangul(String text) {
        return text != null && text.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
    }

    private boolean brokenLatex(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        long dollars = markdown.chars().filter(ch -> ch == '$').count();
        return dollars % 2 != 0 || markdown.contains("\"$") || markdown.contains("$\"") || markdown.contains("@$");
    }

    private boolean singleUnpairedDollar(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        long dollars = text.chars().filter(ch -> ch == '$').count();
        if (dollars != 1) {
            return false;
        }
        String value = text.trim();
        return !value.matches("^\\$[^$]+\\$$");
    }

    private double qualityScore(List<String> issues, double shortLineRatio, double jamoLineRatio,
            NormalizedDocument document, String markdown) {
        double score = Math.max(0.0d, 1.0d - Math.min(0.45d, shortLineRatio * 1.5d));
        score -= Math.min(0.35d, jamoLineRatio * 5.0d);
        score -= mathRetentionPenalty(document, markdown);
        if (issues != null) {
            for (String issue : issues) {
                score -= switch (issue) {
                    case "MARKDOWN_BLANK", "NO_NORMALIZED_BLOCKS" -> 1.0d;
                    case "FRAGMENTED_SHORT_LINES", "BROKEN_LATEX_DELIMITER" -> 0.20d;
                    case "MATH_OCR_REVIEW_REQUIRED", "MATH_NOT_RENDERED_AS_MARKDOWN", "MATH_RENDERING_LOSS" -> 0.20d;
                    case "KOREAN_JAMO_REVIEW_REQUIRED", "KOREAN_TEXT_GARBLING",
                            "KOREAN_TEXT_OCR_INCOMPLETE", "CONTENT_PAGE_COVERAGE_INCOMPLETE" -> 0.20d;
                    case "PYMUPDF_FALLBACK_USED" -> 0.15d;
                    default -> 0.03d;
                };
            }
        }
        return Math.max(0.0d, score);
    }

    private double mathRetentionPenalty(NormalizedDocument document, String markdown) {
        if (document == null) {
            return 0.0d;
        }
        long mathBlocks = document.blocks().stream().filter(block -> block.hasText())
                .filter(block -> block.text().contains("$") || block.text().contains("\\frac")
                        || block.text().contains("\\sqrt")
                        || block.text().matches(".*[0-9A-Za-z][=+\\-*/^][0-9A-Za-z].*"))
                .count();
        if (mathBlocks < 20) {
            return 0.0d;
        }
        long rendered = markdown == null ? 0L : markdown.lines().filter(this::formulaLine).count();
        double retention = (double) rendered / mathBlocks;
        return retention >= 0.20d ? 0.0d : Math.min(0.30d, (0.20d - retention) * 1.5d);
    }

    private boolean fatalIssue(String issue) {
        return "MARKDOWN_BLANK".equals(issue)
                || "NO_NORMALIZED_BLOCKS".equals(issue);
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private String trimBlankEdges(List<String> lines) {
        int start = 0;
        int end = lines.size();
        while (start < end && lines.get(start).isBlank()) {
            start++;
        }
        while (end > start && lines.get(end - 1).isBlank()) {
            end--;
        }
        return String.join("\n", lines.subList(start, end)).trim();
    }
}
