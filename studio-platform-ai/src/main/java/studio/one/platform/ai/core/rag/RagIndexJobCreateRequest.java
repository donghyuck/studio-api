package studio.one.platform.ai.core.rag;

import java.util.Map;
import java.util.Objects;

public final class RagIndexJobCreateRequest {

    private final String objectType;
    private final String objectId;
    private final String documentId;
    private final String sourceType;
    private final boolean forceReindex;
    private final RagIndexRequest indexRequest;
    private final String sourceName;

    public RagIndexJobCreateRequest(
            String objectType,
            String objectId,
            String documentId,
            String sourceType,
            boolean forceReindex,
            RagIndexRequest indexRequest,
            String sourceName
    ) {
                objectType = normalize(objectType);
                objectId = normalize(objectId);
                sourceType = normalize(sourceType);
                sourceName = normalizeSourceName(sourceName);
                if (indexRequest != null) {
                    documentId = normalize(documentId);
                    if (documentId == null) {
                        documentId = indexRequest.documentId();
                    }
                    if (sourceName == null) {
                        sourceName = displayName(indexRequest.metadata(), documentId);
                    }
                } else {
                    documentId = normalize(documentId);
                    if (sourceName == null) {
                        sourceName = displayName(null, documentId);
                    }
                }
        
        this.objectType = objectType;
        this.objectId = objectId;
        this.documentId = documentId;
        this.sourceType = sourceType;
        this.forceReindex = forceReindex;
        this.indexRequest = indexRequest;
        this.sourceName = sourceName;
    }

    public String objectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
    }

    public String documentId() {
        return documentId;
    }

    public String sourceType() {
        return sourceType;
    }

    public boolean forceReindex() {
        return forceReindex;
    }

    public RagIndexRequest indexRequest() {
        return indexRequest;
    }

    public String sourceName() {
        return sourceName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagIndexJobCreateRequest)) {
            return false;
        }
        RagIndexJobCreateRequest that = (RagIndexJobCreateRequest) o;
        return java.util.Objects.equals(objectType, that.objectType)
                && java.util.Objects.equals(objectId, that.objectId)
                && java.util.Objects.equals(documentId, that.documentId)
                && java.util.Objects.equals(sourceType, that.sourceType)
                && forceReindex == that.forceReindex
                && java.util.Objects.equals(indexRequest, that.indexRequest)
                && java.util.Objects.equals(sourceName, that.sourceName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(objectType, objectId, documentId, sourceType, forceReindex, indexRequest, sourceName);
    }

    @Override
    public String toString() {
        return "RagIndexJobCreateRequest[" +
                "objectType=" + objectType + ", " +
                "objectId=" + objectId + ", " +
                "documentId=" + documentId + ", " +
                "sourceType=" + sourceType + ", " +
                "forceReindex=" + forceReindex + ", " +
                "indexRequest=" + indexRequest + ", " +
                "sourceName=" + sourceName +
                "]";
    }

    public RagIndexJobCreateRequest(
            String objectType,
            String objectId,
            String documentId,
            String sourceType,
            boolean forceReindex,
            RagIndexRequest indexRequest) {
        this(objectType, objectId, documentId, sourceType, forceReindex, indexRequest, null);
    }

    public String requiredDocumentId() {
        return Objects.requireNonNull(documentId, "documentId must not be blank");
    }

    public static RagIndexJobCreateRequest forIndexRequest(RagIndexRequest indexRequest) {
        Objects.requireNonNull(indexRequest, "indexRequest");
        Map<String, Object> metadata = indexRequest.metadata();
        return new RagIndexJobCreateRequest(
                text(metadata.get("objectType")),
                text(metadata.get("objectId")),
                indexRequest.documentId(),
                text(metadata.get("sourceType")),
                false,
                indexRequest,
                displayName(metadata, indexRequest.documentId()));
    }

    private static String displayName(Map<String, Object> metadata, String documentId) {
        String sourceName = metadata == null
                ? null
                : firstText(metadata, "sourceName", "title", "filename", "fileName", "name");
        return sourceName == null ? normalize(documentId) : sourceName;
    }

    private static String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            String value = text(metadata.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : normalize(value.toString());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeSourceName(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() <= RagIndexJob.MAX_SOURCE_NAME_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, RagIndexJob.MAX_SOURCE_NAME_LENGTH);
    }
}
