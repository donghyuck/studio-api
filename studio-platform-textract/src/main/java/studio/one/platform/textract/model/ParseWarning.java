package studio.one.platform.textract.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Non-fatal warning emitted during parsing.
 */
public record ParseWarning(
        String code,
        String message,
        String path,
        Map<String, Object> metadata) {

    public static final String KEY_CANONICAL_CODE = "canonicalCode";
    public static final String KEY_SEVERITY = "severity";
    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_BLOCK_REF = "blockRef";
    public static final String KEY_PARTIAL_PARSE = "partialParse";

    public ParseWarning {
        code = code == null ? "unknown" : code;
        message = message == null ? "" : message;
        path = path == null ? "" : path;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ParseWarning warning(String code, String message, String sourceRef) {
        return warning(code, message, sourceRef, Map.of());
    }

    public static ParseWarning warning(String code, String message, String sourceRef, Map<String, Object> metadata) {
        return structured(code, message, sourceRef, sourceRef, "", false, ParseWarningSeverity.WARNING, metadata);
    }

    public static ParseWarning partial(String code, String message, String sourceRef, Map<String, Object> metadata) {
        return structured(code, message, sourceRef, sourceRef, "", true, ParseWarningSeverity.WARNING, metadata);
    }

    public static ParseWarning partial(
            String code,
            String message,
            String sourceRef,
            String blockRef,
            Map<String, Object> metadata) {
        // blockRef is an optional logical block pointer inside the same sourceRef scope.
        return structured(code, message, sourceRef, sourceRef, blockRef, true, ParseWarningSeverity.WARNING, metadata);
    }

    public static ParseWarning error(String code, String message, String sourceRef, Map<String, Object> metadata) {
        return structured(code, message, sourceRef, sourceRef, "", false, ParseWarningSeverity.ERROR, metadata);
    }

    public String canonicalCode() {
        Object value = metadata.get(KEY_CANONICAL_CODE);
        return value instanceof String stringValue && !stringValue.isBlank()
                ? stringValue
                : normalizeCode(code);
    }

    public ParseWarningSeverity severity() {
        Object value = metadata.get(KEY_SEVERITY);
        if (value instanceof String stringValue) {
            try {
                return ParseWarningSeverity.valueOf(stringValue);
            } catch (IllegalArgumentException ignored) {
                return ParseWarningSeverity.WARNING;
            }
        }
        return ParseWarningSeverity.WARNING;
    }

    public String sourceRef() {
        Object value = metadata.get(KEY_SOURCE_REF);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : path;
    }

    public String blockRef() {
        Object value = metadata.get(KEY_BLOCK_REF);
        return value instanceof String stringValue ? stringValue : "";
    }

    public boolean partialParse() {
        Object value = metadata.get(KEY_PARTIAL_PARSE);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    public boolean isError() {
        return severity() == ParseWarningSeverity.ERROR;
    }

    private static ParseWarning structured(
            String code,
            String message,
            String path,
            String sourceRef,
            String blockRef,
            boolean partialParse,
            ParseWarningSeverity severity,
            Map<String, Object> metadata) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.putIfAbsent(KEY_CANONICAL_CODE, normalizeCode(code));
        merged.putIfAbsent(KEY_SEVERITY, severity.name());
        merged.putIfAbsent(KEY_SOURCE_REF, sourceRef == null || sourceRef.isBlank() ? path : sourceRef);
        merged.putIfAbsent(KEY_BLOCK_REF, blockRef == null ? "" : blockRef);
        merged.putIfAbsent(KEY_PARTIAL_PARSE, partialParse);
        return new ParseWarning(code, message, path, merged);
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "UNKNOWN";
        }
        return code
                .trim()
                .replace('.', '_')
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
}
