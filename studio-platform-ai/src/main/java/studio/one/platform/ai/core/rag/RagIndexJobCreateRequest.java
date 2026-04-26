package studio.one.platform.ai.core.rag;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RagIndexJobCreateRequest(
        String objectType,
        String objectId,
        String documentId,
        String sourceType,
        boolean forceReindex,
        RagIndexRequest indexRequest,
        Map<String, Object> metadata,
        List<String> keywords,
        boolean useLlmKeywordExtraction) {

    public RagIndexJobCreateRequest {
        objectType = normalize(objectType);
        objectId = normalize(objectId);
        sourceType = normalize(sourceType);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        if (indexRequest != null) {
            documentId = normalize(documentId);
            if (documentId == null) {
                documentId = indexRequest.documentId();
            }
            if (metadata.isEmpty()) {
                metadata = indexRequest.metadata();
            }
            if (keywords.isEmpty()) {
                keywords = indexRequest.keywords();
            }
            useLlmKeywordExtraction = useLlmKeywordExtraction || indexRequest.useLlmKeywordExtraction();
        } else {
            documentId = normalize(documentId);
        }
    }

    public RagIndexJobCreateRequest(
            String objectType,
            String objectId,
            String documentId,
            String sourceType,
            boolean forceReindex,
            RagIndexRequest indexRequest) {
        this(objectType, objectId, documentId, sourceType, forceReindex, indexRequest, Map.of(), List.of(), false);
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
                metadata,
                indexRequest.keywords(),
                indexRequest.useLlmKeywordExtraction());
    }

    private static String text(Object value) {
        return value == null ? null : normalize(value.toString());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
