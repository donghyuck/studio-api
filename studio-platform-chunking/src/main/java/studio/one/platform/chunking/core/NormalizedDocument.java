package studio.one.platform.chunking.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Parser-neutral structured document input for chunking.
 */
public class NormalizedDocument {
    private final String sourceDocumentId;
    private final String plainText;
    private final String sourceFormat;
    private final String filename;
    private final List<NormalizedBlock> blocks;
    private final Map<String, Object> metadata;


    public NormalizedDocument(
            String sourceDocumentId,
            String plainText,
            String sourceFormat,
            String filename,
            List<NormalizedBlock> blocks,
            Map<String, Object> metadata) {
        sourceDocumentId = normalize(sourceDocumentId);
        plainText = plainText == null ? "" : plainText.trim();
        sourceFormat = normalize(sourceFormat);
        filename = normalize(filename);
        blocks = blocks == null ? List.of() : Collections.unmodifiableList(blocks.stream()
                .filter(block -> block != null)
                .filter(NormalizedBlock::hasText)
                .sorted(Comparator.comparing(
                        NormalizedBlock::order,
                        Comparator.nullsLast(Integer::compareTo)))
                .collect(java.util.stream.Collectors.toList()));
        metadata = sanitize(metadata);
    
        this.sourceDocumentId = sourceDocumentId;
        this.plainText = plainText;
        this.sourceFormat = sourceFormat;
        this.filename = filename;
        this.blocks = blocks;
        this.metadata = metadata;
    }

    public String chunkableText() {
        if (!plainText.isBlank()) {
            return plainText;
        }
        return blocks.stream()
                .map(NormalizedBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    public ChunkingContext.Builder toContextBuilder() {
        return ChunkingContext.builder(chunkableText())
                .sourceDocumentId(sourceDocumentId)
                .filename(filename)
                .contentType(sourceFormat)
                .objectType(stringMetadata(ChunkMetadata.KEY_OBJECT_TYPE))
                .objectId(stringMetadata(ChunkMetadata.KEY_OBJECT_ID))
                .metadata(metadata);
    }

    private String stringMetadata(String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    public static Builder builder(String sourceDocumentId) {
        return new Builder(sourceDocumentId);
    }

    private static Map<String, Object> sanitize(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            if (value instanceof String && ((String) value).isBlank()) {
                return;
            }
            sanitized.put(key, value);
        });
        return Map.copyOf(sanitized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private final String sourceDocumentId;
        private String plainText;
        private String sourceFormat;
        private String filename;
        private List<NormalizedBlock> blocks = List.of();
        private Map<String, Object> metadata = Map.of();

        private Builder(String sourceDocumentId) {
            this.sourceDocumentId = sourceDocumentId;
        }

        public Builder plainText(String plainText) {
            this.plainText = plainText;
            return this;
        }

        public Builder sourceFormat(String sourceFormat) {
            this.sourceFormat = sourceFormat;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder blocks(List<NormalizedBlock> blocks) {
            this.blocks = blocks;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public NormalizedDocument build() {
            return new NormalizedDocument(sourceDocumentId, plainText, sourceFormat, filename, blocks, metadata);
        }
    }

    public String sourceDocumentId() {
        return sourceDocumentId;
    }

    public String plainText() {
        return plainText;
    }

    public String sourceFormat() {
        return sourceFormat;
    }

    public String filename() {
        return filename;
    }

    public List<NormalizedBlock> blocks() {
        return blocks;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }
}
