package studio.one.platform.team.application.usecase;

import studio.one.platform.team.application.command.DryRunTeamMigrationCommand;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.domain.model.TeamMigrationRef;

public interface TeamMigrationService {
    TeamMigrationRef dryRun(DryRunTeamMigrationCommand command);
    TeamMigrationRef apply(String runId, TeamAccessContext actor);
    TeamMigrationRef get(String runId, TeamAccessContext actor);
    TeamMigrationRef verify(String runId, TeamAccessContext actor);
    TeamMigrationRef rollback(String runId, TeamAccessContext actor);
}
