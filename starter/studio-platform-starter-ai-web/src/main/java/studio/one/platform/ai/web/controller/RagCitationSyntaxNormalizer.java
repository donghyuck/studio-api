package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes supported visual variants without inventing citation numbers.
 */
public final class RagCitationSyntaxNormalizer {

    private static final Pattern ALTERNATIVE = Pattern.compile(
            "(?:【\\s*(\\d{1,9}(?:\\s*,\\s*\\d{1,9})*)\\s*】"
                    + "|［\\s*(\\d{1,9}(?:\\s*,\\s*\\d{1,9})*)\\s*］"
                    + "|\\[\\s*근거\\s+(\\d{1,9}(?:\\s*,\\s*\\d{1,9})*)\\s*])");
    private static final String CITATION_GROUP = "\\[\\s*\\d{1,9}(?:\\s*,\\s*\\d{1,9})*\\s*]";
    private static final Pattern CITATION_ONLY_LINE = Pattern.compile(
            "^\\s*((?:" + CITATION_GROUP + "\\s*)+)[.!?]?\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile(
            "^\\s*\\|?(?:\\s*:?-{3,}:?\\s*\\|)+\\s*$");

    public String normalize(String content) {
        Matcher matcher = ALTERNATIVE.matcher(content == null ? "" : content);
        StringBuffer normalized = new StringBuffer();
        while (matcher.find()) {
            String indexes = matcher.group(1) != null
                    ? matcher.group(1)
                    : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            matcher.appendReplacement(normalized, Matcher.quoteReplacement("[" + indexes + "]"));
        }
        matcher.appendTail(normalized);
        return attachStandaloneCitations(normalized.toString());
    }

    private String attachStandaloneCitations(String content) {
        String normalizedNewlines = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalizedNewlines.split("\n", -1);
        List<String> output = new ArrayList<>(lines.length);
        boolean fencedCode = false;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fencedCode = !fencedCode;
                output.add(line);
                continue;
            }
            Matcher citationOnly = CITATION_ONLY_LINE.matcher(line);
            if (!fencedCode && citationOnly.matches()) {
                int target = previousSubstantiveLine(output);
                if (target >= 0) {
                    output.set(target, output.get(target).stripTrailing() + " " + citationOnly.group(1).strip());
                    continue;
                }
            }
            output.add(line);
        }
        return String.join("\n", output);
    }

    private int previousSubstantiveLine(List<String> lines) {
        for (int index = lines.size() - 1; index >= 0; index--) {
            String candidate = lines.get(index).strip();
            if (candidate.isBlank()) {
                continue;
            }
            if (candidate.startsWith("#")
                    || candidate.startsWith("```")
                    || candidate.startsWith("~~~")
                    || TABLE_SEPARATOR.matcher(candidate).matches()) {
                return -1;
            }
            return index;
        }
        return -1;
    }
}
