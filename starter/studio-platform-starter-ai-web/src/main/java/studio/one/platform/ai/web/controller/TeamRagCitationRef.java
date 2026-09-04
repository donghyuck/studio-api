package studio.one.platform.ai.web.controller;

/**
 * Minimal stored citation identity used for live Team permission checks.
 */
public record TeamRagCitationRef(
        Long workspaceId,
        String objectType,
        String objectId,
        String revisionId) {

    public TeamRagCitationRef {
        if (workspaceId == null || workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId must be positive");
        }
        objectType = required(objectType, "objectType");
        objectId = required(objectId, "objectId");
        revisionId = normalize(revisionId);
    }

    private static String required(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
