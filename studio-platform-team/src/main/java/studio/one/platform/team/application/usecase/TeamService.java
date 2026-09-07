package studio.one.platform.team.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.team.application.command.CreateTeamCommand;
import studio.one.platform.team.application.command.TeamListQuery;
import studio.one.platform.team.application.command.UpdateTeamCommand;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.domain.model.TeamRef;

public interface TeamService {

    TeamRef create(CreateTeamCommand command);

    TeamRef update(Long teamId, UpdateTeamCommand command);

    TeamRef archive(Long teamId, TeamAccessContext actor);

    TeamRef get(Long teamId, TeamAccessContext actor);

    Page<TeamRef> list(TeamListQuery query, Pageable pageable, TeamAccessContext actor);
}
