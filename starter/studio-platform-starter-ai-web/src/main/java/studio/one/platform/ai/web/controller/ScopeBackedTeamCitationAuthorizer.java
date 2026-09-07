package studio.one.platform.ai.web.controller;

import studio.one.platform.ai.core.rag.team.TeamCitationAuthorizer;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;

/**
 * Re-resolves the current authorized manifest for every citation access decision.
 */
public final class ScopeBackedTeamCitationAuthorizer implements TeamCitationAuthorizer {

    private final TeamRagScopeResolver scopeResolver;

    public ScopeBackedTeamCitationAuthorizer(TeamRagScopeResolver scopeResolver) {
        if (scopeResolver == null) {
            throw new IllegalArgumentException("scopeResolver must not be null");
        }
        this.scopeResolver = scopeResolver;
    }

    @Override
    public boolean canRead(
            Long teamId,
            Long workspaceId,
            String objectType,
            String objectId,
            String revisionId) {
        try {
            return scopeResolver.resolveAuthorized(teamId, workspaceId)
                    .map(manifest -> manifest.contains(objectType, objectId, revisionId))
                    .orElse(false);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
