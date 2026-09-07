package studio.one.platform.ai.core.rag.team;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded Workspace set for migration and corpus manifest contributors.
 */
public record TeamKnowledgeContributionRequest(
        Long teamId,
        Set<Long> workspaceIds,
        int maxSources) {

    public TeamKnowledgeContributionRequest {
        if (teamId == null || teamId <= 0) {
            throw new IllegalArgumentException("teamId must be positive");
        }
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            throw new IllegalArgumentException("workspaceIds must not be empty");
        }
        LinkedHashSet<Long> sanitized = new LinkedHashSet<>();
        workspaceIds.stream().sorted().forEach(workspaceId -> {
            if (workspaceId == null || workspaceId <= 0) {
                throw new IllegalArgumentException("workspaceId must be positive");
            }
            sanitized.add(workspaceId);
        });
        workspaceIds = Set.copyOf(sanitized);
        if (maxSources <= 0) {
            throw new IllegalArgumentException("maxSources must be positive");
        }
    }
}
