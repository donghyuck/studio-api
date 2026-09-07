package studio.one.platform.team.application.command;

import java.util.List;

public record DryRunTeamMigrationCommand(
        String idempotencyKey,
        Long targetTeamId,
        List<Long> sourceRootWorkspaceIds,
        TeamAccessContext actor) {
}
