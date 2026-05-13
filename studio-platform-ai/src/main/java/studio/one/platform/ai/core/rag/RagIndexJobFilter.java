package studio.one.platform.ai.core.rag;

public final class RagIndexJobFilter {

    private final RagIndexJobStatus status;
    private final String objectType;
    private final String objectId;
    private final String documentId;

    public RagIndexJobFilter(
            RagIndexJobStatus status,
            String objectType,
            String objectId,
            String documentId
    ) {
                objectType = normalize(objectType);
                objectId = normalize(objectId);
                documentId = normalize(documentId);
        
        this.status = status;
        this.objectType = objectType;
        this.objectId = objectId;
        this.documentId = documentId;
    }

    public RagIndexJobStatus status() {
        return status;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagIndexJobFilter)) {
            return false;
        }
        RagIndexJobFilter that = (RagIndexJobFilter) o;
        return java.util.Objects.equals(status, that.status)
                && java.util.Objects.equals(objectType, that.objectType)
                && java.util.Objects.equals(objectId, that.objectId)
                && java.util.Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(status, objectType, objectId, documentId);
    }

    @Override
    public String toString() {
        return "RagIndexJobFilter[" +
                "status=" + status + ", " +
                "objectType=" + objectType + ", " +
                "objectId=" + objectId + ", " +
                "documentId=" + documentId +
                "]";
    }

    public static RagIndexJobFilter empty() {
        return new RagIndexJobFilter(null, null, null, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
