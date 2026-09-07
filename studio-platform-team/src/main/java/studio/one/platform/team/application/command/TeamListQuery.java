package studio.one.platform.team.application.command;

import studio.one.platform.team.domain.model.TeamVisibility;
import studio.one.platform.team.domain.model.TeamStatus;

public record TeamListQuery(
        String keyword,
        Long companyId,
        TeamVisibility visibility,
        TeamStatus status) {

    public static TeamListQuery all() {
        return new TeamListQuery(null, null, null, TeamStatus.ACTIVE);
    }
}
