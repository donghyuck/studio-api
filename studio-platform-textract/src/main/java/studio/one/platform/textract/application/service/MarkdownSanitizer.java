package studio.one.platform.textract.application.service;

public class MarkdownSanitizer {

    public String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public String inline(String value) {
        return sanitize(value)
                .replace("|", "\\|")
                .replace('\n', ' ')
                .trim();
    }
}
