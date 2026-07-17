package studio.one.platform.markdown.autoconfigure;

import java.util.regex.Pattern;

final class OcrTextNormalizer {

    private static final Pattern SHORT_LATIN_NOISE = Pattern.compile("^[A-Za-z\\s!|/\\\\.,:;~_-]{2,24}$");
    private static final Pattern ICON_NOISE = Pattern.compile("(?i)^(OO+|Oo|oo|O00|00|SS|SAS|BS|BA|SHS|NOS|Sis!?|eT|loin|Bal|xm|me)$");
    private static final Pattern SHORT_LATIN_DIGIT_NOISE = Pattern.compile("(?i)^[A-Za-z]{1,5}[\\s0-9O|/\\\\.,:;~_!<>-]{1,8}$");
    private static final Pattern SHORT_DIGIT_LATIN_NOISE = Pattern.compile("(?i)^[0-9O]{1,4}[A-Za-z]{1,5}[A-Za-z\\s0-9O|/\\\\.,:;~_!<>-]{0,6}$");
    private static final Pattern SHORT_SYMBOL_LATIN_NOISE = Pattern.compile("(?i)^[\\s|/\\\\.,:;~_!<>-]*[A-Za-z]{1,5}[A-Za-z\\s|/\\\\.,:;~_!<>-]*$");
    private static final Pattern HAS_HANGUL = Pattern.compile(".*[가-힣].*");
    private static final Pattern HAS_MATH = Pattern.compile(".*[0-9=+\\-*/^≤≥≠√×÷°].*");

    private OcrTextNormalizer() {
    }

    static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.contains("\n") || text.contains("\r")) {
            return normalizeLines(text);
        }
        String normalized = text
                .replace('—', '-')
                .replace('–', '-')
                .replace('−', '-')
                .replace('Ｌ', 'L')
                .replace('ㆍ', '·')
                .replace('《', '<')
                .replace('》', '>')
                .replaceAll("(?<=[0-9A-Za-z)\\]}])ㅡ(?=[0-9A-Za-z({\\[])", "-")
                .replaceAll("(?<=\\d)\\s*ㅡ\\s*(?=\\d|[A-Za-z])", "-")
                .replaceAll("(?<=[A-Za-z])\\s*ㅡ\\s*(?=\\d)", "-")
                .replaceAll("^\\s*ㅡ\\s*(?=\\d|[A-Za-z])", "-")
                .replaceAll("(?<=[0-9A-Za-z])\\s+ㅡ\\s*(?=[0-9A-Za-z])", " - ")
                .replaceAll("(?<=[=+\\-*/^({\\[])\\s+ㅡ\\s*(?=[0-9A-Za-z])", " -")
                .replaceAll("(?<=[=+\\-*/^({\\[])ㅡ(?=[0-9A-Za-z])", "-")
                .replaceAll("ㅁ\\s*로", "므로")
                .replaceAll("[□■ㅁ]{2,}", "")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .trim();
        if (isDiscardableNoise(normalized)) {
            return "";
        }
        return normalized;
    }

    static boolean hasSuspiciousJamo(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.chars()
                .filter(ch -> ch != 'ㄱ' && ch != 'ㄴ' && ch != 'ㄷ')
                .anyMatch(ch -> (ch >= 0x3131 && ch <= 0x318E) || ch == 0xFFFD);
    }

    static boolean isDiscardableNoiseLine(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return isDiscardableNoise(text.trim());
    }

    private static String normalizeLines(String text) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String line : text.split("\\R")) {
            String normalized = normalize(line);
            if (!normalized.isBlank()) {
                lines.add(normalized);
            }
        }
        return mergeFragments(lines);
    }

    private static String mergeFragments(java.util.List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        java.util.List<String> merged = new java.util.ArrayList<>();
        for (String line : lines) {
            if (!merged.isEmpty() && mergeable(merged.get(merged.size() - 1), line)) {
                int last = merged.size() - 1;
                merged.set(last, merged.get(last) + line);
            } else {
                merged.add(line);
            }
        }
        return String.join("\n", merged).trim();
    }

    private static boolean mergeable(String previous, String next) {
        if (previous == null || next == null || previous.isBlank() || next.isBlank()) {
            return false;
        }
        if (next.length() > 5 || next.matches(".*[.!?。]$") || previous.matches(".*[.!?。]$")) {
            return false;
        }
        boolean nextHangul = next.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
        boolean previousHangul = previous.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
        return nextHangul && previousHangul && !previous.endsWith(" ");
    }

    private static boolean isDiscardableNoise(String text) {
        if (ICON_NOISE.matcher(text).matches()) {
            return true;
        }
        if (text.length() <= 12
                && !HAS_HANGUL.matcher(text).matches()
                && (SHORT_LATIN_DIGIT_NOISE.matcher(text).matches()
                        || SHORT_DIGIT_LATIN_NOISE.matcher(text).matches()
                        || SHORT_SYMBOL_LATIN_NOISE.matcher(text).matches())
                && !text.matches(".*[=+*/^≤≥≠√×÷].*")) {
            return true;
        }
        if (text.length() > 24 || HAS_HANGUL.matcher(text).matches() || HAS_MATH.matcher(text).matches()) {
            return false;
        }
        if (!SHORT_LATIN_NOISE.matcher(text).matches()) {
            return false;
        }
        long letters = text.chars().filter(Character::isLetter).count();
        return letters >= 2;
    }
}
