package studio.one.platform.ai.core.rag;

public record RagIndexJobFilter(
        RagIndexJobStatus status,
        String objectType,
        String objectId,
        String documentId) {

    public RagIndexJobFilter {
        objectType = normalize(objectType);
        objectId = normalize(objectId);
        documentId = normalize(documentId);
    }

    public static RagIndexJobFilter empty() {
        return new RagIndexJobFilter(null, null, null, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
