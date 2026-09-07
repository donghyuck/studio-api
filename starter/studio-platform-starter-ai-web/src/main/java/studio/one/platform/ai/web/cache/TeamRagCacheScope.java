package studio.one.platform.ai.web.cache;

import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;

/**
 * Team-specific cache isolation values. All values are server-resolved.
 */
public record TeamRagCacheScope(
        Long teamId,
        Long workspaceId,
        String corpusRevisionId,
        String corpusFingerprint,
        String permissionVersion) {

    public TeamRagCacheScope {
        if (teamId == null || teamId <= 0) {
            throw new IllegalArgumentException("teamId must be positive");
        }
        if (workspaceId != null && workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId must be positive");
        }
        corpusRevisionId = required(corpusRevisionId, "corpusRevisionId");
        corpusFingerprint = required(corpusFingerprint, "corpusFingerprint");
        permissionVersion = required(permissionVersion, "permissionVersion");
    }

    public static TeamRagCacheScope from(TeamKnowledgeManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest must not be null");
        }
        return new TeamRagCacheScope(
                manifest.teamId(),
                manifest.workspaceId(),
                manifest.corpusRevisionId(),
                manifest.corpusFingerprint(),
                manifest.permissionVersion());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
