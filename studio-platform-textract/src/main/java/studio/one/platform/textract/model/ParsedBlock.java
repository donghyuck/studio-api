package studio.one.platform.textract.model;

import java.util.List;
import java.util.Map;

/**
 * RAG-friendly logical content block.
 */
public record ParsedBlock(
        String id,
        BlockType type,
        String path,
        String text,
        Integer page,
        List<ParsedBlock> children,
        Map<String, Object> metadata) {

    public ParsedBlock {
        id = id == null ? path : id;
        type = type == null ? BlockType.UNKNOWN : type;
        path = path == null ? "" : path;
        text = text == null ? "" : text;
        children = children == null ? List.of() : List.copyOf(children);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ParsedBlock text(String path, BlockType type, String text) {
        return new ParsedBlock(path, type, path, text, null, List.of(), Map.of());
    }
}
