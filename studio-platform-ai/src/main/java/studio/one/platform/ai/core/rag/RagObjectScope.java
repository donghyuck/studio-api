package studio.one.platform.ai.core.rag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Existing vector object scope reused by aggregate retrieval boundaries such as Team RAG.
 */
public record RagObjectScope(
        String objectType,
        String objectId,
        Set<String> partitionIds) {

    public RagObjectScope {
        objectType = required(objectType, "objectType");
        objectId = required(objectId, "objectId");
        partitionIds = sanitize(partitionIds);
    }

    public RagObjectScope(String objectType, String objectId) {
        this(objectType, objectId, Set.of());
    }

    public String canonicalValue() {
        return objectType + ":" + objectId + ":" + String.join(",", partitionIds.stream().sorted().toList());
    }

    private static Set<String> sanitize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .sorted()
                .forEach(sanitized::add);
        return Set.copyOf(sanitized);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
