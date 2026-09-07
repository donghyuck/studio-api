package studio.one.platform.team.application.usecase;

import studio.one.platform.team.application.command.TeamAccessContext;

@FunctionalInterface
public interface TeamCompanyAssignmentPort {
    void assertCanAssign(Long companyId, TeamAccessContext actor);
}
