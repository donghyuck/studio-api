package studio.one.platform.ai.core.vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RAG chunk record persisted by a vector store.
 * <p>
 * Standard metadata keys such as {@link #KEY_CHUNK_INDEX},
 * {@link #KEY_PREVIOUS_CHUNK_ID}, {@link #KEY_NEXT_CHUNK_ID},
 * {@link #KEY_TENANT_ID}, {@link #KEY_CREATED_AT}, and {@link #KEY_INDEXED_AT}
 * are pass-through metadata keys. They are intentionally not promoted to
 * constructor fields until the core contract has a stable need for first-class
 * accessors.
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

    /**
     * Creates a vector record.
     * <p>
     * Prefer {@link #builder()} for new call sites because this constructor has
     * many adjacent {@link String} parameters and is retained for binary/source
     * compatibility.
     */
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

    public static Builder builder() {
        return new Builder();
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

    public static final class Builder {

        private String id;
        private String documentId;
        private String chunkId;
        private String parentChunkId;
        private String contentHash;
        private String text;
        private List<Double> embedding;
        private String embeddingModel;
        private Integer embeddingDimension;
        private String chunkType;
        private String headingPath;
        private String sourceRef;
        private Integer page;
        private Integer slide;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder documentId(String documentId) {
            this.documentId = documentId;
            return this;
        }

        public Builder chunkId(String chunkId) {
            this.chunkId = chunkId;
            return this;
        }

        public Builder parentChunkId(String parentChunkId) {
            this.parentChunkId = parentChunkId;
            return this;
        }

        public Builder contentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder embedding(List<Double> embedding) {
            this.embedding = embedding;
            return this;
        }

        public Builder embeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder embeddingDimension(int embeddingDimension) {
            this.embeddingDimension = embeddingDimension;
            return this;
        }

        public Builder chunkType(String chunkType) {
            this.chunkType = chunkType;
            return this;
        }

        public Builder headingPath(String headingPath) {
            this.headingPath = headingPath;
            return this;
        }

        public Builder sourceRef(String sourceRef) {
            this.sourceRef = sourceRef;
            return this;
        }

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public Builder slide(Integer slide) {
            this.slide = slide;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public VectorRecord build() {
            List<Double> vector = Objects.requireNonNull(embedding, "embedding");
            int dimension = embeddingDimension == null ? vector.size() : embeddingDimension;
            return new VectorRecord(
                    id,
                    documentId,
                    chunkId,
                    parentChunkId,
                    contentHash,
                    text,
                    vector,
                    embeddingModel,
                    dimension,
                    chunkType,
                    headingPath,
                    sourceRef,
                    page,
                    slide,
                    metadata);
        }
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
            if (normalizedKey != null && value != null) {
                sanitized.put(normalizedKey, value);
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
