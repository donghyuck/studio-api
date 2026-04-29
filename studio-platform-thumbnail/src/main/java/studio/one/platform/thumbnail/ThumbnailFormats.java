package studio.one.platform.thumbnail;

import java.util.Locale;
import java.util.Set;

public final class ThumbnailFormats {

    public static final String DEFAULT_FORMAT = "png";

    private static final Set<String> SUPPORTED_FORMATS = Set.of(DEFAULT_FORMAT);

    private ThumbnailFormats() {
    }

    public static String normalize(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported thumbnail format: " + format);
        }
        return normalized;
    }

    public static String normalizeOrDefault(String format, String defaultFormat) {
        String normalized = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (SUPPORTED_FORMATS.contains(normalized)) {
            return normalized;
        }
        String normalizedDefault = defaultFormat == null ? "" : defaultFormat.trim().toLowerCase(Locale.ROOT);
        if (SUPPORTED_FORMATS.contains(normalizedDefault)) {
            return normalizedDefault;
        }
        return DEFAULT_FORMAT;
    }

    public static String contentType(String format) {
        return "image/png";
    }
}
