package studio.one.platform.text.extractor.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import studio.one.platform.text.extractor.FileParser;

public abstract class AbstractFileParser implements FileParser {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    protected String safeFilename(String filename) {
        return filename != null ? filename : "unknown";
    }

    protected String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }

    protected boolean hasExtension(String filename, String... exts) {
        if (filename == null)
            return false;
        String lower = lower(filename);
        for (String ext : exts) {
            if (lower.endsWith(ext))
                return true;
        }
        return false;
    }

    protected boolean isContentType(String contentType, String... types) {
        if (contentType == null)
            return false;
        String lower = lower(contentType);
        for (String t : types) {
            if (lower.startsWith(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 기본적인 정제: 제어문자 제거, CRLF 정규화, 과도한 공백 줄 축소.
     * 한글/다국어 텍스트는 그대로 유지한다.
     */
    protected String cleanText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String normalized = raw
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        normalized = CONTROL_CHARS.matcher(normalized).replaceAll("");
        normalized = MULTI_BLANK_LINES.matcher(normalized).replaceAll("\n\n");
        return normalized.trim();
    }
}
