package studio.one.platform.ai.core;

import java.util.Map;

/**
 * Provider-neutral metadata filter for retrieval requests.
 * <p>
 * The first supported convention is object scope filtering through
 * {@code objectType}/{@code objectId}. Additional metadata predicates can be
 * added later without replacing existing request contracts.
 */
public final class MetadataFilter {

    private static final MetadataFilter EMPTY = new MetadataFilter(null, null);

    private final String objectType;
    private final String objectId;

    public MetadataFilter(String objectType, String objectId) {
        this.objectType = normalize(objectType);
        this.objectId = normalize(objectId);
    }

    public static MetadataFilter empty() {
        return EMPTY;
    }

    public static MetadataFilter objectScope(String objectType, String objectId) {
        MetadataFilter filter = new MetadataFilter(objectType, objectId);
        return filter.hasObjectScope() ? filter : EMPTY;
    }

    public String objectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
    }

    public boolean hasObjectScope() {
        return objectType != null || objectId != null;
    }

    public boolean isEmpty() {
        return !hasObjectScope();
    }

    public boolean matchesObjectScope(Map<String, Object> metadata) {
        if (!hasObjectScope()) {
            return true;
        }
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        boolean typeOk = objectType == null || objectType.equalsIgnoreCase(stringValue(metadata.get("objectType")));
        boolean idOk = objectId == null || objectId.equals(stringValue(metadata.get("objectId")));
        return typeOk && idOk;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
