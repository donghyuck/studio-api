package studio.one.platform.textract.model;

import java.util.Map;

/**
 * Text fragment extracted from a source document.
 */
public record ExtractedText(String path, String text, Map<String, Object> metadata) {

    public ExtractedText {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
