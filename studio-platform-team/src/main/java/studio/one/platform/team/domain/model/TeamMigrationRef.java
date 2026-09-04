package studio.one.platform.team.domain.model;

import java.time.Instant;
import java.util.List;

public record TeamMigrationRef(
        String runId,
        String idempotencyKey,
        Long targetTeamId,
        List<Long> sourceRootWorkspaceIds,
        TeamMigrationStatus status,
        String beforeSnapshotReference,
        String workspaceSnapshotReference,
        String knowledgeSnapshotReference,
        long workspaceCount,
        long memberCount,
        long attachmentCount,
        long wikiCount,
        long webSourceCount,
        long vectorCount,
        String sourceChecksum,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt) {

    public TeamMigrationRef {
        sourceRootWorkspaceIds = sourceRootWorkspaceIds == null ? List.of() : List.copyOf(sourceRootWorkspaceIds);
    }
}
