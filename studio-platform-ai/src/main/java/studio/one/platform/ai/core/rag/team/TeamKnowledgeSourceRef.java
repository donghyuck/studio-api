package studio.one.platform.ai.core.rag.team;

import java.util.Set;

import studio.one.platform.ai.core.rag.RagObjectScope;

/**
 * Immutable reference from a Team corpus to an already indexed object.
 */
public record TeamKnowledgeSourceRef(
        Long teamId,
        Long workspaceId,
        TeamKnowledgeSourceType sourceType,
        String objectType,
        String objectId,
        String revisionId,
        Set<String> partitionIds) {

    public TeamKnowledgeSourceRef {
        teamId = positive(teamId, "teamId");
        workspaceId = positive(workspaceId, "workspaceId");
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType must not be null");
        }
        RagObjectScope scope = new RagObjectScope(objectType, objectId, partitionIds);
        objectType = scope.objectType();
        objectId = scope.objectId();
        partitionIds = scope.partitionIds();
        revisionId = normalize(revisionId);
    }

    public RagObjectScope objectScope() {
        return new RagObjectScope(objectType, objectId, partitionIds);
    }

    public String canonicalValue() {
        return String.join("|",
                teamId.toString(),
                workspaceId.toString(),
                sourceType.name(),
                objectScope().canonicalValue(),
                revisionId == null ? "" : revisionId);
    }

    private static Long positive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
