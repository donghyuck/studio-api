package studio.one.platform.ai.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral metadata filter for retrieval requests.
 * <p>
 * The first supported convention is object scope filtering through
 * {@code objectType}/{@code objectId}. Supplying only one of the two fields is
 * intentional and means "filter by this single metadata key". Additional
 * metadata predicates can be added later without replacing existing request
 * contracts.
 * <p>
 * {@link #objectScope(String, String)} and {@link #of(Map, Map, Map)} use the
 * same convention: when {@code equalsCriteria} contains {@code objectType} or
 * {@code objectId}, {@link #hasObjectScope()} returns {@code true} and
 * {@link #objectType()}/{@link #objectId()} expose those normalized values.
 */
public final class MetadataFilter {

    private static final String KEY_OBJECT_TYPE = "objectType";
    private static final String KEY_OBJECT_ID = "objectId";
    private static final MetadataFilter EMPTY = new MetadataFilter(Map.of(), Map.of(), Map.of());

    private final String objectType;
    private final String objectId;
    private final Map<String, Object> equalsCriteria;
    private final Map<String, List<Object>> inCriteria;
    private final Map<String, MetadataRange<?>> rangeCriteria;

    public MetadataFilter(String objectType, String objectId) {
        this(objectScopeEquals(objectType, objectId), Map.of(), Map.of());
    }

    public MetadataFilter(
            Map<String, Object> equalsCriteria,
            Map<String, List<Object>> inCriteria,
            Map<String, MetadataRange<?>> rangeCriteria) {
        this.equalsCriteria = sanitizeEquals(equalsCriteria);
        this.inCriteria = sanitizeIn(inCriteria);
        this.rangeCriteria = sanitizeRanges(rangeCriteria);
        this.objectType = normalize(stringValue(this.equalsCriteria.get(KEY_OBJECT_TYPE)));
        this.objectId = normalize(stringValue(this.equalsCriteria.get(KEY_OBJECT_ID)));
    }

    public static MetadataFilter empty() {
        return EMPTY;
    }

    public static MetadataFilter objectScope(String objectType, String objectId) {
        MetadataFilter filter = new MetadataFilter(objectType, objectId);
        return filter.hasObjectScope() ? filter : EMPTY;
    }

    /**
     * Creates a general metadata filter.
     * <p>
     * {@code equalsCriteria} keys named {@code objectType} and {@code objectId}
     * are also treated as the legacy object scope convention.
     */
    public static MetadataFilter of(
            Map<String, Object> equalsCriteria,
            Map<String, List<Object>> inCriteria,
            Map<String, MetadataRange<?>> rangeCriteria) {
        MetadataFilter filter = new MetadataFilter(equalsCriteria, inCriteria, rangeCriteria);
        return filter.isEmpty() ? EMPTY : filter;
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
        return equalsCriteria.isEmpty() && inCriteria.isEmpty() && rangeCriteria.isEmpty();
    }

    public Map<String, Object> equalsCriteria() {
        return equalsCriteria;
    }

    public Map<String, List<Object>> inCriteria() {
        return inCriteria;
    }

    public Map<String, MetadataRange<?>> rangeCriteria() {
        return rangeCriteria;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MetadataFilter that)) {
            return false;
        }
        return Objects.equals(objectType, that.objectType)
                && Objects.equals(objectId, that.objectId)
                && Objects.equals(equalsCriteria, that.equalsCriteria)
                && Objects.equals(inCriteria, that.inCriteria)
                && Objects.equals(rangeCriteria, that.rangeCriteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectType, objectId, equalsCriteria, inCriteria, rangeCriteria);
    }

    @Override
    public String toString() {
        return "MetadataFilter{"
                + "objectType='" + objectType + '\''
                + ", objectId='" + objectId + '\''
                + ", equalsCriteria=" + equalsCriteria
                + ", inCriteria=" + inCriteria
                + ", rangeCriteria=" + rangeCriteria
                + '}';
    }

    private static Map<String, Object> objectScopeEquals(String objectType, String objectId) {
        Map<String, Object> values = new LinkedHashMap<>();
        String normalizedObjectType = normalize(objectType);
        String normalizedObjectId = normalize(objectId);
        if (normalizedObjectType != null) {
            values.put(KEY_OBJECT_TYPE, normalizedObjectType);
        }
        if (normalizedObjectId != null) {
            values.put(KEY_OBJECT_ID, normalizedObjectId);
        }
        return values;
    }

    private static Map<String, Object> sanitizeEquals(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = normalize(key);
            if (normalizedKey != null && value != null
                    && (!(value instanceof String text) || !text.isBlank())) {
                sanitized.put(normalizedKey, value instanceof String text ? text.trim() : value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static Map<String, List<Object>> sanitizeIn(Map<String, List<Object>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Object>> sanitized = new LinkedHashMap<>();
        values.forEach((key, candidates) -> {
            String normalizedKey = normalize(key);
            if (normalizedKey == null || candidates == null || candidates.isEmpty()) {
                return;
            }
            List<Object> sanitizedCandidates = candidates.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> !(value instanceof String text) || !text.isBlank())
                    .map(value -> value instanceof String text ? text.trim() : value)
                    .distinct()
                    .toList();
            if (!sanitizedCandidates.isEmpty()) {
                sanitized.put(normalizedKey, sanitizedCandidates);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static Map<String, MetadataRange<?>> sanitizeRanges(Map<String, MetadataRange<?>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, MetadataRange<?>> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = normalize(key);
            if (normalizedKey != null && value != null) {
                sanitized.put(normalizedKey, value);
            }
        });
        return Map.copyOf(sanitized);
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
