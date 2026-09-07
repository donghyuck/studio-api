package studio.one.platform.ai.core.rag.team;

import java.util.List;

import studio.one.platform.ai.core.rag.RagObjectScope;

/**
 * Authorized snapshot of Team knowledge sources for one Team or Workspace subtree.
 */
public record TeamKnowledgeManifest(
        Long teamId,
        Long workspaceId,
        String corpusRevisionId,
        String corpusFingerprint,
        String permissionVersion,
        List<TeamKnowledgeSourceRef> sources) {

    public TeamKnowledgeManifest {
        if (teamId == null || teamId <= 0) {
            throw new IllegalArgumentException("teamId must be positive");
        }
        if (workspaceId != null && workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId must be positive");
        }
        corpusRevisionId = required(corpusRevisionId, "corpusRevisionId");
        permissionVersion = required(permissionVersion, "permissionVersion");
        sources = TeamKnowledgeFingerprint.ordered(sources);
        if (sources.stream().anyMatch(source -> !teamId.equals(source.teamId()))) {
            throw new IllegalArgumentException("all sources must belong to the requested Team");
        }
        corpusFingerprint = normalize(corpusFingerprint);
        String calculated = TeamKnowledgeFingerprint.create(teamId, workspaceId, sources);
        if (corpusFingerprint == null) {
            corpusFingerprint = calculated;
        } else if (!corpusFingerprint.equals(calculated)) {
            throw new IllegalArgumentException("corpusFingerprint does not match sources");
        }
    }

    public static TeamKnowledgeManifest create(
            Long teamId,
            Long workspaceId,
            String corpusRevisionId,
            String permissionVersion,
            List<TeamKnowledgeSourceRef> sources) {
        return new TeamKnowledgeManifest(
                teamId, workspaceId, corpusRevisionId, null, permissionVersion, sources);
    }

    public List<RagObjectScope> objectScopes() {
        return sources.stream().map(TeamKnowledgeSourceRef::objectScope).distinct().toList();
    }

    public boolean contains(String objectType, String objectId, String revisionId) {
        String normalizedRevision = normalize(revisionId);
        return sources.stream().anyMatch(source -> source.objectType().equals(objectType)
                && source.objectId().equals(objectId)
                && (normalizedRevision == null || normalizedRevision.equals(source.revisionId())));
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
