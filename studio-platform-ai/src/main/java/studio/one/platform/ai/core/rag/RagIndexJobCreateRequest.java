package studio.one.platform.ai.core.rag;

import java.util.Map;
import java.util.Objects;

public record RagIndexJobCreateRequest(
        String objectType,
        String objectId,
        String documentId,
        String sourceType,
        boolean forceReindex,
        RagIndexRequest indexRequest) {

    public RagIndexJobCreateRequest {
        objectType = normalize(objectType);
        objectId = normalize(objectId);
        sourceType = normalize(sourceType);
        if (indexRequest != null) {
            documentId = normalize(documentId);
            if (documentId == null) {
                documentId = indexRequest.documentId();
            }
        } else {
            documentId = normalize(documentId);
        }
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
                indexRequest);
    }

    private static String text(Object value) {
        return value == null ? null : normalize(value.toString());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
