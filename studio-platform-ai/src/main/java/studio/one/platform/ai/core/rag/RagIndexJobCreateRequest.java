package studio.one.platform.ai.core.rag;

import java.util.Map;
import java.util.Objects;

public record RagIndexJobCreateRequest(
        String objectType,
        String objectId,
        String documentId,
        String sourceType,
        boolean forceReindex,
        RagIndexRequest indexRequest,
        String sourceName) {

    public RagIndexJobCreateRequest {
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
