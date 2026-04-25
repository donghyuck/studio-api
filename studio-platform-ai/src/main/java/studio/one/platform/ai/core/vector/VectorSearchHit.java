package studio.one.platform.ai.core.vector;

import java.util.Map;
import java.util.Objects;

/**
 * RAG-oriented vector search hit with chunk provenance.
 */
public final class VectorSearchHit {

    private final String id;
    private final String documentId;
    private final String chunkId;
    private final String parentChunkId;
    private final String text;
    private final double score;
    private final String chunkType;
    private final String headingPath;
    private final String sourceRef;
    private final Integer page;
    private final Integer slide;
    private final Map<String, Object> metadata;

    public VectorSearchHit(
            String id,
            String documentId,
            String chunkId,
            String parentChunkId,
            String text,
            double score,
            String chunkType,
            String headingPath,
            String sourceRef,
            Integer page,
            Integer slide,
            Map<String, Object> metadata) {
        this.id = Objects.requireNonNull(id, "id");
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.chunkId = chunkId;
        this.parentChunkId = parentChunkId;
        this.text = text;
        this.score = score;
        this.chunkType = chunkType;
        this.headingPath = headingPath;
        this.sourceRef = sourceRef;
        this.page = page;
        this.slide = slide;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static VectorSearchHit from(VectorSearchResult result) {
        return from(result, true, true);
    }

    public static VectorSearchHit from(VectorSearchResult result, boolean includeText, boolean includeMetadata) {
        Objects.requireNonNull(result, "result");
        VectorDocument document = result.document();
        Map<String, Object> metadata = document.metadata();
        String documentId = stringMetadata(metadata, VectorRecord.KEY_DOCUMENT_ID, document.id());
        return new VectorSearchHit(
                document.id(),
                documentId,
                stringMetadata(metadata, VectorRecord.KEY_CHUNK_ID, document.id()),
                stringMetadata(metadata, VectorRecord.KEY_PARENT_CHUNK_ID, null),
                includeText ? document.content() : null,
                result.score(),
                stringMetadata(metadata, VectorRecord.KEY_CHUNK_TYPE, null),
                stringMetadata(metadata, VectorRecord.KEY_HEADING_PATH, null),
                stringMetadata(metadata, VectorRecord.KEY_SOURCE_REF, null),
                integerMetadata(metadata, VectorRecord.KEY_PAGE),
                integerMetadata(metadata, VectorRecord.KEY_SLIDE),
                includeMetadata ? metadata : Map.of());
    }

    public String id() {
        return id;
    }

    public String documentId() {
        return documentId;
    }

    public String chunkId() {
        return chunkId;
    }

    public String parentChunkId() {
        return parentChunkId;
    }

    public String text() {
        return text;
    }

    public double score() {
        return score;
    }

    public String chunkType() {
        return chunkType;
    }

    public String headingPath() {
        return headingPath;
    }

    public String sourceRef() {
        return sourceRef;
    }

    public Integer page() {
        return page;
    }

    public Integer slide() {
        return slide;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    private static String stringMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isBlank() ? fallback : text;
    }

    private static Integer integerMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        return null;
    }
}
