package studio.one.platform.textract.domain.model;

import java.util.Map;

/**
 * Source location mapped to an offset range in normalized Markdown.
 */
public record MarkdownLocator(
        String type,
        Integer number,
        String title,
        int startOffset,
        int endOffset,
        String sourceRef,
        Map<String, Object> metadata) {

    public MarkdownLocator {
        type = type == null ? "" : type;
        title = title == null ? "" : title;
        sourceRef = sourceRef == null ? "" : sourceRef;
        startOffset = Math.max(0, startOffset);
        endOffset = Math.max(startOffset, endOffset);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
