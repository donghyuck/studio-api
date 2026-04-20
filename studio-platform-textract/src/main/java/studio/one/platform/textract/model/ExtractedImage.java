package studio.one.platform.textract.model;

import java.util.Map;

/**
 * Image reference extracted from a source document.
 */
public record ExtractedImage(
        String path,
        String contentType,
        String filename,
        Integer width,
        Integer height,
        Map<String, Object> metadata) {

    public ExtractedImage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
