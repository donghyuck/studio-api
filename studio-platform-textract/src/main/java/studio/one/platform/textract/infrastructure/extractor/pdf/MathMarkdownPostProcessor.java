package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

public class MathMarkdownPostProcessor {

    public static final String KEY_MATH_MARKDOWN_APPLIED = "mathMarkdownApplied";
    public static final String KEY_MATH_MARKDOWN_EXPRESSION_COUNT = "mathMarkdownExpressionCount";
    public static final String KEY_MATH_MARKDOWN_ENGINE = "mathMarkdownEngine";
    public static final String KEY_MATH_MARKDOWN_QUALITY = "mathMarkdownQuality";
    public static final String KEY_MATH_DOCUMENT_ENGINE_REQUIRED = "mathDocumentEngineRequired";

    private static final Pattern EQUATION_LINE = Pattern.compile(".*[=≤≥≠].*");
    private static final Pattern MATH_SPAN = Pattern.compile(
            "(?<![$`])([A-Za-z0-9(){}\\[\\].,+\\-*/^_=≤≥≠∞πθαβγΔ√×÷²³⁴⁵⁶⁷⁸⁹⁰°—–−'’\\s]{2,}[=≤≥≠][A-Za-z0-9(){}\\[\\].,+\\-*/^_=≤≥≠∞πθαβγΔ√×÷²³⁴⁵⁶⁷⁸⁹⁰°—–−'’\\s]{1,})(?![$`])");

    public ParsedFile process(ParsedFile file) {
        if (file == null) {
            return null;
        }
        Counter counter = new Counter();
        String markdown = convertDocument(markdownText(file), counter);
        String plainText = convertDocument(file.plainText(), new Counter());
        List<ParsedBlock> blocks = file.blocks().stream()
                .map(block -> block(block, new Counter()))
                .toList();
        List<ParsedBlock> pages = file.pages().stream()
                .map(page -> block(page, new Counter()))
                .toList();

        Map<String, Object> metadata = new LinkedHashMap<>(file.metadata());
        metadata.put(KEY_MATH_MARKDOWN_APPLIED, counter.count > 0);
        metadata.put(KEY_MATH_MARKDOWN_EXPRESSION_COUNT, counter.count);
        metadata.put(KEY_MATH_MARKDOWN_ENGINE, "heuristic-v1");
        metadata.put(KEY_MATH_MARKDOWN_QUALITY, "REVIEW_REQUIRED");
        metadata.put(KEY_MATH_DOCUMENT_ENGINE_REQUIRED, true);

        return new ParsedFile(
                file.format(),
                plainText,
                blocks,
                metadata,
                file.warnings(),
                pages,
                file.tables(),
                file.images(),
                file.ocrApplied(),
                markdown,
                "markdown",
                file.locators());
    }

    private ParsedBlock block(ParsedBlock block, Counter counter) {
        if (block == null) {
            return null;
        }
        List<ParsedBlock> children = block.children().stream()
                .map(child -> block(child, counter))
                .toList();
        Map<String, Object> metadata = new LinkedHashMap<>(block.metadata());
        String converted = convertText(block.text(), counter);
        if (!converted.equals(block.text())) {
            metadata.put(KEY_MATH_MARKDOWN_APPLIED, true);
        }
        return new ParsedBlock(block.id(), block.type(), block.path(), converted, block.page(), children, metadata);
    }

    private String convertDocument(String text, Counter counter) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            lines.add(convertText(line, counter));
        }
        return String.join("\n", lines).trim();
    }

    private String convertText(String text, Counter counter) {
        if (text == null || text.isBlank() || hasMarkdownMath(text)) {
            return text == null ? "" : text;
        }
        String normalizedText = cleanOcrText(text);
        String trimmed = normalizedText.trim();
        if (wholeLineExpression(trimmed)) {
            counter.count++;
            return "$" + latex(trimmed) + "$";
        }
        Matcher matcher = MATH_SPAN.matcher(normalizedText);
        StringBuffer result = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            if (!looksLikeExpression(expression)) {
                continue;
            }
            counter.count++;
            changed = true;
            matcher.appendReplacement(result, Matcher.quoteReplacement("$" + latex(expression) + "$"));
        }
        matcher.appendTail(result);
        return changed ? result.toString() : normalizedText;
    }

    private boolean wholeLineExpression(String text) {
        if (text.length() > 160 || !looksLikeExpression(text)) {
            return false;
        }
        long hangul = text.chars().filter(ch -> ch >= 0xAC00 && ch <= 0xD7A3).count();
        return hangul == 0;
    }

    private boolean looksLikeExpression(String text) {
        if (text == null || text.length() < 3) {
            return false;
        }
        if (EQUATION_LINE.matcher(text).matches()) {
            return true;
        }
        long operators = text.chars().filter(ch -> "+-*/^=≤≥≠√×÷".indexOf(ch) >= 0).count();
        long variables = text.chars().filter(ch -> Character.isLetter(ch) || ch == 'π' || ch == 'θ').count();
        return operators >= 2 && variables >= 1;
    }

    private String latex(String expression) {
        String value = expression
                .replace('—', '-')
                .replace('–', '-')
                .replace('−', '-')
                .replace('Ｌ', 'L')
                .replace('ㆍ', '·')
                .replace('×', '·')
                .replace("÷", "\\div ")
                .replace("≤", "\\le ")
                .replace("≥", "\\ge ")
                .replace("≠", "\\ne ")
                .replace("∞", "\\infty ")
                .replace("√", "\\sqrt");
        value = value.replaceAll("([A-Za-z])°", "$1^{2}");
        value = value.replaceAll("([A-Za-z0-9)])\\s*['’]", "$1'");
        value = normalizeSuperscripts(value);
        value = value.replaceAll("\\s+", " ").trim();
        value = value.replaceAll("([A-Za-z0-9)])\\^([A-Za-z0-9])", "$1^{$2}");
        return value;
    }

    private String cleanOcrText(String text) {
        return text
                .replace('—', '-')
                .replace('–', '-')
                .replace('−', '-')
                .replace('Ｌ', 'L')
                .replace('ㆍ', '·')
                .replaceAll("[□■ㅁ]{2,}", "")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .trim();
    }

    private String normalizeSuperscripts(String value) {
        return value
                .replace("⁰", "^{0}")
                .replace("¹", "^{1}")
                .replace("²", "^{2}")
                .replace("³", "^{3}")
                .replace("⁴", "^{4}")
                .replace("⁵", "^{5}")
                .replace("⁶", "^{6}")
                .replace("⁷", "^{7}")
                .replace("⁸", "^{8}")
                .replace("⁹", "^{9}");
    }

    private boolean hasMarkdownMath(String text) {
        int dollars = text.length() - text.replace("$", "").length();
        return dollars >= 2
                || text.contains("\\(")
                || text.contains("\\[")
                || text.contains("\\frac")
                || text.contains("\\sqrt")
                || text.contains("\\begin{");
    }

    private String markdownText(ParsedFile file) {
        return file.markdown() == null || file.markdown().isBlank() ? file.plainText() : file.markdown();
    }

    private static final class Counter {
        private int count;
    }
}
