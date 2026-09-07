package studio.one.platform.team.application.usecase;

import java.util.List;

public interface TeamMigrationWorkspacePort {

    WorkspaceSnapshot captureBeforeSnapshot(List<Long> sourceRootWorkspaceIds);

    void assignToTeam(String runId, Long targetTeamId, List<Long> sourceRootWorkspaceIds, String snapshotReference);

    MigrationVerification verifyAssignment(
            String runId,
            Long targetTeamId,
            List<Long> sourceRootWorkspaceIds,
            String snapshotReference);

    void rollbackAssignment(
            String runId,
            Long targetTeamId,
            List<Long> sourceRootWorkspaceIds,
            String snapshotReference);

    record WorkspaceSnapshot(String reference, long workspaceCount, long memberCount) {
    }

    record MigrationVerification(boolean matches, String details) {
    }
}
