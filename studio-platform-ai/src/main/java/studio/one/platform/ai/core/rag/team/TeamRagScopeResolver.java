package studio.one.platform.ai.core.rag.team;

import java.util.Optional;

/**
 * Resolves only sources readable by the current principal.
 * Implementations must hide the difference between a missing and denied Team.
 */
public interface TeamRagScopeResolver {

    Optional<TeamKnowledgeManifest> resolveAuthorized(Long teamId, Long workspaceId);
}
