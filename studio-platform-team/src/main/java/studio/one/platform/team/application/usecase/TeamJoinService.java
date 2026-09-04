package studio.one.platform.team.application.usecase;

import java.util.List;

import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.domain.model.TeamJoinRequestRef;
import studio.one.platform.team.domain.model.TeamJoinRequestStatus;
import studio.one.platform.team.domain.model.TeamJoinResult;

public interface TeamJoinService {
    TeamJoinResult join(Long teamId, TeamAccessContext actor);
    List<TeamJoinRequestRef> getRequests(Long teamId, TeamJoinRequestStatus status, TeamAccessContext actor);
    TeamJoinRequestRef approve(Long teamId, Long requestId, TeamAccessContext actor);
    TeamJoinRequestRef reject(Long teamId, Long requestId, TeamAccessContext actor);
}
