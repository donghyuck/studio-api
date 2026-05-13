package studio.one.platform.textract.domain.model;

import java.util.Map;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Text fragment extracted from a source document.
 */
@Value
@Accessors(fluent = true)
public class ExtractedText {
    String path;
    String text;
    Map<String, Object> metadata;

    public ExtractedText(String path, String text, Map<String, Object> metadata) {
        this.path = path;
        this.text = text;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
