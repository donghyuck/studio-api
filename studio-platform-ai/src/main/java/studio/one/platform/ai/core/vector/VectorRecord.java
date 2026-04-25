package studio.one.platform.ai.core.vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RAG chunk record persisted by a vector store.
 */
public final class VectorRecord {

    public static final String KEY_TENANT_ID = "tenantId";
    public static final String KEY_OBJECT_TYPE = "objectType";
    public static final String KEY_OBJECT_ID = "objectId";
    public static final String KEY_DOCUMENT_ID = "documentId";
    public static final String KEY_CHUNK_ID = "chunkId";
    public static final String KEY_PARENT_CHUNK_ID = "parentChunkId";
    public static final String KEY_CHUNK_INDEX = "chunkIndex";
    public static final String KEY_PREVIOUS_CHUNK_ID = "previousChunkId";
    public static final String KEY_NEXT_CHUNK_ID = "nextChunkId";
    public static final String KEY_CHUNK_TYPE = "chunkType";
    public static final String KEY_HEADING_PATH = "headingPath";
    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_PAGE = "page";
    public static final String KEY_SLIDE = "slide";
    public static final String KEY_CONTENT_HASH = "contentHash";
    public static final String KEY_EMBEDDING_MODEL = "embeddingModel";
    public static final String KEY_EMBEDDING_DIMENSION = "embeddingDimension";
    public static final String KEY_CREATED_AT = "createdAt";
    public static final String KEY_INDEXED_AT = "indexedAt";

    private final String id;
    private final String documentId;
    private final String chunkId;
    private final String parentChunkId;
    private final String contentHash;
    private final String text;
    private final List<Double> embedding;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final String chunkType;
    private final String headingPath;
    private final String sourceRef;
    private final Integer page;
    private final Integer slide;
    private final Map<String, Object> metadata;

    public VectorRecord(
            String id,
            String documentId,
            String chunkId,
            String parentChunkId,
            String contentHash,
            String text,
            List<Double> embedding,
            String embeddingModel,
            int embeddingDimension,
            String chunkType,
            String headingPath,
            String sourceRef,
            Integer page,
            Integer slide,
            Map<String, Object> metadata) {
        this.id = requireText(id, "id");
        this.documentId = requireText(documentId, "documentId");
        this.chunkId = requireText(chunkId, "chunkId");
        this.parentChunkId = normalize(parentChunkId);
        this.contentHash = requireText(contentHash, "contentHash");
        this.text = requireText(text, "text");
        this.embedding = List.copyOf(Objects.requireNonNull(embedding, "embedding"));
        if (this.embedding.isEmpty()) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be greater than zero");
        }
        if (embeddingDimension != this.embedding.size()) {
            throw new IllegalArgumentException("embeddingDimension must match embedding size");
        }
        this.embeddingModel = requireText(embeddingModel, "embeddingModel");
        this.embeddingDimension = embeddingDimension;
        this.chunkType = normalize(chunkType);
        this.headingPath = normalize(headingPath);
        this.sourceRef = normalize(sourceRef);
        this.page = page;
        this.slide = slide;
        this.metadata = sanitize(metadata);
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

    public String contentHash() {
        return contentHash;
    }

    public String text() {
        return text;
    }

    public List<Double> embedding() {
        return embedding;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public int embeddingDimension() {
        return embeddingDimension;
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

    public Map<String, Object> toMetadata() {
        Map<String, Object> values = new LinkedHashMap<>(metadata);
        put(values, KEY_DOCUMENT_ID, documentId);
        put(values, KEY_CHUNK_ID, chunkId);
        put(values, KEY_PARENT_CHUNK_ID, parentChunkId);
        put(values, KEY_CHUNK_TYPE, chunkType);
        put(values, KEY_HEADING_PATH, headingPath);
        put(values, KEY_SOURCE_REF, sourceRef);
        put(values, KEY_PAGE, page);
        put(values, KEY_SLIDE, slide);
        put(values, KEY_CONTENT_HASH, contentHash);
        put(values, KEY_EMBEDDING_MODEL, embeddingModel);
        put(values, KEY_EMBEDDING_DIMENSION, embeddingDimension);
        return Map.copyOf(values);
    }

    public VectorDocument toVectorDocument() {
        return new VectorDocument(id, text, toMetadata(), embedding);
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        if (value != null && (!(value instanceof String textValue) || !textValue.isBlank())) {
            values.put(key, value);
        }
    }

    private static Map<String, Object> sanitize(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = normalize(key);
            if (normalizedKey != null && value != null
                    && (!(value instanceof String textValue) || !textValue.isBlank())) {
                sanitized.put(normalizedKey, value instanceof String textValue ? textValue.trim() : value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
