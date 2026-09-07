package studio.one.platform.team.application.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.command.TeamMemberCommand;
import studio.one.platform.team.application.error.TeamConflictException;
import studio.one.platform.team.application.error.TeamNotFoundException;
import studio.one.platform.team.application.error.TeamValidationException;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamMemberService;
import studio.one.platform.team.domain.model.TeamMemberRef;
import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.team.domain.model.TeamRole;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberJpaRepository;

public class DefaultTeamMemberService implements TeamMemberService {
    private final TeamJpaRepository teamRepository;
    private final TeamMemberJpaRepository memberRepository;
    private final TeamAuthorizationPort authorizationPort;

    public DefaultTeamMemberService(
            TeamJpaRepository teamRepository,
            TeamMemberJpaRepository memberRepository,
            TeamAuthorizationPort authorizationPort) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.authorizationPort = authorizationPort;
    }

    @Override
    @Transactional
    public TeamMemberRef addMember(Long teamId, TeamMemberCommand command) {
        TeamAccessContext actor = requireActor(command.actor());
        assertManage(teamId, actor);
        TeamEntity team = teamForUpdate(teamId);
        Long userId = requireUserId(command.userId());
        TeamRole role = requireRole(command.role());
        assertCanManageRole(teamId, actor, null, role);
        if (memberRepository.findByTeamIdAndUserId(teamId, userId).isPresent()) {
            throw new TeamConflictException("Team member already exists: " + userId);
        }
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(TeamMemberStatus.ACTIVE);
        member.setCreatedBy(actor.requireUserId());
        member.setUpdatedBy(actor.requireUserId());
        TeamMemberRef result = memberRepository.save(member).toRef();
        bumpPermissionVersion(team, actor.requireUserId());
        return result;
    }

    @Override
    @Transactional
    public TeamMemberRef changeRole(Long teamId, TeamMemberCommand command) {
        TeamAccessContext actor = requireActor(command.actor());
        assertManage(teamId, actor);
        TeamEntity team = teamForUpdate(teamId);
        TeamMemberEntity member = memberRepository.findByTeamIdAndUserIdAndStatus(
                        teamId, requireUserId(command.userId()), TeamMemberStatus.ACTIVE)
                .orElseThrow(() -> new TeamNotFoundException("Team member not found: " + command.userId()));
        TeamRole role = requireRole(command.role());
        assertCanManageRole(teamId, actor, member.getRole(), role);
        if (member.getRole() == TeamRole.OWNER && role != TeamRole.OWNER) {
            assertNotLastOwner(teamId);
        }
        member.setRole(role);
        member.setUpdatedBy(actor.requireUserId());
        TeamMemberRef result = memberRepository.save(member).toRef();
        bumpPermissionVersion(team, actor.requireUserId());
        return result;
    }

    @Override
    @Transactional
    public void removeMember(Long teamId, Long userId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        assertManage(teamId, resolved);
        TeamEntity team = teamForUpdate(teamId);
        TeamMemberEntity member = memberRepository.findByTeamIdAndUserIdAndStatus(
                        teamId, requireUserId(userId), TeamMemberStatus.ACTIVE)
                .orElseThrow(() -> new TeamNotFoundException("Team member not found: " + userId));
        if (member.getRole() == TeamRole.OWNER) {
            assertNotLastOwner(teamId);
        }
        assertCanManageRole(teamId, resolved, member.getRole(), null);
        memberRepository.delete(member);
        bumpPermissionVersion(team, resolved.requireUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberRef> getMembers(Long teamId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        if (!resolved.platformAdmin()) {
            authorizationPort.assertGranted(teamId, resolved.requireUserId(), TeamPermissionActions.MEMBER_READ);
        }
        return memberRepository.findByTeamIdAndStatusOrderByUserIdAsc(teamId, TeamMemberStatus.ACTIVE).stream()
                .map(TeamMemberEntity::toRef)
                .toList();
    }

    private void assertManage(Long teamId, TeamAccessContext actor) {
        if (!actor.platformAdmin()) {
            authorizationPort.assertGranted(teamId, actor.requireUserId(), TeamPermissionActions.MEMBER_MANAGE);
        }
    }

    private void assertCanManageRole(
            Long teamId,
            TeamAccessContext actor,
            TeamRole currentRole,
            TeamRole requestedRole) {
        if (actor.platformAdmin()) {
            return;
        }
        TeamRole actorRole = authorizationPort.findEffectiveRole(teamId, actor.requireUserId()).orElse(null);
        if (actorRole == TeamRole.OWNER) {
            return;
        }
        if (actorRole != TeamRole.ADMIN
                || currentRole != null && currentRole != TeamRole.MEMBER
                || requestedRole != null && requestedRole != TeamRole.MEMBER) {
            throw new AccessDeniedException("Only a Team owner can manage admin or owner roles");
        }
    }

    private TeamEntity teamForUpdate(Long teamId) {
        return teamRepository.findForUpdate(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));
    }

    private void assertNotLastOwner(Long teamId) {
        if (memberRepository.countByTeamIdAndRoleAndStatus(teamId, TeamRole.OWNER, TeamMemberStatus.ACTIVE) <= 1) {
            throw new TeamConflictException("Team must retain at least one active owner");
        }
    }

    private void bumpPermissionVersion(TeamEntity team, Long actorUserId) {
        team.setPermissionVersion(team.getPermissionVersion() + 1L);
        team.setUpdatedBy(actorUserId);
        teamRepository.save(team);
    }

    private TeamAccessContext requireActor(TeamAccessContext actor) {
        if (actor == null) {
            throw new TeamValidationException("team actor is required");
        }
        actor.requireUserId();
        return actor;
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new TeamValidationException("team member userId is required");
        }
        return userId;
    }

    private TeamRole requireRole(TeamRole role) {
        if (role == null) {
            throw new TeamValidationException("team member role is required");
        }
        return role;
    }
}
