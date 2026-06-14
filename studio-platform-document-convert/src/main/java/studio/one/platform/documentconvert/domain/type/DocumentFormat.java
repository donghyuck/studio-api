package studio.one.platform.documentconvert.domain.type;

import java.util.Locale;
import java.util.Set;

public enum DocumentFormat {
    MARKDOWN("md"),
    HTML("html"),
    DOCX("docx"),
    PDF("pdf"),
    TEXT("txt");

    private static final Set<String> SUPPORTED = Set.of(
            "MARKDOWN:HTML", "MARKDOWN:DOCX", "MARKDOWN:PDF",
            "HTML:DOCX", "HTML:PDF", "DOCX:MARKDOWN", "DOCX:HTML");

    private final String extension;

    DocumentFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static DocumentFormat parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("document format is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("MD".equals(normalized)) {
            normalized = "MARKDOWN";
        }
        return valueOf(normalized);
    }

    public boolean canConvertTo(DocumentFormat target) {
        return target != null && SUPPORTED.contains(name() + ":" + target.name());
    }
}
