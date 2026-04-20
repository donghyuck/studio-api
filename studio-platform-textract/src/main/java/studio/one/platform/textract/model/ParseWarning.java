package studio.one.platform.textract.model;

import java.util.Map;

/**
 * Non-fatal warning emitted during parsing.
 */
public record ParseWarning(String code, String message, String path, Map<String, Object> metadata) {

    public ParseWarning {
        code = code == null ? "unknown" : code;
        message = message == null ? "" : message;
        path = path == null ? "" : path;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
