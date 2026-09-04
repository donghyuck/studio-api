package studio.one.platform.team.application.usecase;

import java.util.List;

import studio.one.platform.team.application.command.TeamMemberCommand;
import studio.one.platform.team.domain.model.TeamMemberRef;
import studio.one.platform.team.domain.model.TeamRole;
import studio.one.platform.team.application.command.TeamAccessContext;

public interface TeamMemberService {

    TeamMemberRef addMember(Long teamId, TeamMemberCommand command);

    TeamMemberRef changeRole(Long teamId, TeamMemberCommand command);

    void removeMember(Long teamId, Long userId, TeamAccessContext actor);

    List<TeamMemberRef> getMembers(Long teamId, TeamAccessContext actor);
}
