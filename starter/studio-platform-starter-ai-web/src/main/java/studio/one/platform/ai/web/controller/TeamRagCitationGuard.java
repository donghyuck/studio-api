package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Objects;

import studio.one.platform.ai.core.rag.team.TeamCitationAuthorizer;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.ai.web.cache.TeamRagCacheScope;

/**
 * Re-resolves current Team scope before stored citations or answers are exposed.
 */
public final class TeamRagCitationGuard {

    private final TeamRagScopeResolver scopeResolver;
    private final TeamCitationAuthorizer citationAuthorizer;

    public TeamRagCitationGuard(
            TeamRagScopeResolver scopeResolver,
            TeamCitationAuthorizer citationAuthorizer) {
        if (scopeResolver == null || citationAuthorizer == null) {
            throw new IllegalArgumentException("scopeResolver and citationAuthorizer are required");
        }
        this.scopeResolver = scopeResolver;
        this.citationAuthorizer = citationAuthorizer;
    }

    public boolean canReadStoredScope(TeamRagCacheScope storedScope, List<TeamRagCitationRef> citations) {
        if (storedScope == null || citations == null) {
            return false;
        }
        TeamKnowledgeManifest current = scopeResolver.resolveAuthorized(
                storedScope.teamId(), storedScope.workspaceId()).orElse(null);
        if (current == null) {
            return false;
        }
        if (!matchesStoredScope(storedScope, current)) {
            return false;
        }
        return citations.stream().allMatch(citation -> current.contains(
                        citation.objectType(), citation.objectId(), citation.revisionId())
                && citationAuthorizer.canRead(
                        current.teamId(),
                        citation.workspaceId(),
                        citation.objectType(),
                        citation.objectId(),
                        citation.revisionId()));
    }

    private boolean matchesStoredScope(TeamRagCacheScope stored, TeamKnowledgeManifest current) {
        return stored.teamId().equals(current.teamId())
                && Objects.equals(stored.workspaceId(), current.workspaceId())
                && stored.corpusRevisionId().equals(current.corpusRevisionId())
                && stored.corpusFingerprint().equals(current.corpusFingerprint())
                && stored.permissionVersion().equals(current.permissionVersion());
    }
}
