package studio.one.platform.team.application.service;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.error.TeamConflictException;
import studio.one.platform.team.application.error.TeamNotFoundException;
import studio.one.platform.team.application.error.TeamValidationException;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamJoinService;
import studio.one.platform.team.domain.model.TeamJoinOutcome;
import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamJoinRequestRef;
import studio.one.platform.team.domain.model.TeamJoinRequestStatus;
import studio.one.platform.team.domain.model.TeamJoinResult;
import studio.one.platform.team.domain.model.TeamMemberRef;
import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.team.domain.model.TeamRole;
import studio.one.platform.team.domain.model.TeamStatus;
import studio.one.platform.team.domain.model.TeamVisibility;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJoinRequestEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJoinRequestJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberJpaRepository;

public class DefaultTeamJoinService implements TeamJoinService {
    private final TeamJpaRepository teamRepository;
    private final TeamMemberJpaRepository memberRepository;
    private final TeamJoinRequestJpaRepository requestRepository;
    private final TeamAuthorizationPort authorizationPort;

    public DefaultTeamJoinService(
            TeamJpaRepository teamRepository,
            TeamMemberJpaRepository memberRepository,
            TeamJoinRequestJpaRepository requestRepository,
            TeamAuthorizationPort authorizationPort) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.authorizationPort = authorizationPort;
    }

    @Override
    @Transactional
    public TeamJoinResult join(Long teamId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        TeamEntity team = teamForUpdate(teamId);
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new TeamConflictException("Archived team cannot accept members");
        }
        Long userId = resolved.requireUserId();
        TeamMemberEntity existingMember = memberRepository
                .findByTeamIdAndUserIdAndStatus(teamId, userId, TeamMemberStatus.ACTIVE)
                .orElse(null);
        if (existingMember != null) {
            return new TeamJoinResult(TeamJoinOutcome.ALREADY_MEMBER, existingMember.toRef(), null);
        }
        if (team.getVisibility() != TeamVisibility.PUBLIC) {
            throw new AccessDeniedException("Private and unlisted teams require an invitation");
        }
        return switch (team.getJoinPolicy()) {
            case OPEN -> joinOpen(team, userId);
            case APPROVAL -> requestApproval(teamId, userId);
            case INVITE_ONLY -> throw new AccessDeniedException("Team accepts invited members only");
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamJoinRequestRef> getRequests(
            Long teamId,
            TeamJoinRequestStatus status,
            TeamAccessContext actor) {
        assertManage(teamId, requireActor(actor));
        List<TeamJoinRequestEntity> requests = status == null
                ? requestRepository.findByTeamIdOrderByRequestedAtAsc(teamId)
                : requestRepository.findByTeamIdAndStatusOrderByRequestedAtAsc(teamId, status);
        return requests.stream().map(TeamJoinRequestEntity::toRef).toList();
    }

    @Override
    @Transactional
    public TeamJoinRequestRef approve(Long teamId, Long requestId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        assertManage(teamId, resolved);
        TeamEntity team = teamForUpdate(teamId);
        TeamJoinRequestEntity request = request(teamId, requestId);
        if (request.getStatus() == TeamJoinRequestStatus.APPROVED) {
            return request.toRef();
        }
        requirePending(request);
        TeamMemberEntity member = memberRepository.findByTeamIdAndUserId(teamId, request.getUserId()).orElse(null);
        if (member == null) {
            member = new TeamMemberEntity();
            member.setTeamId(teamId);
            member.setUserId(request.getUserId());
            member.setCreatedBy(resolved.requireUserId());
        }
        member.setRole(TeamRole.MEMBER);
        member.setStatus(TeamMemberStatus.ACTIVE);
        member.setUpdatedBy(resolved.requireUserId());
        memberRepository.save(member);
        resolve(request, TeamJoinRequestStatus.APPROVED, resolved.requireUserId());
        bumpPermissionVersion(team, resolved.requireUserId());
        return requestRepository.save(request).toRef();
    }

    @Override
    @Transactional
    public TeamJoinRequestRef reject(Long teamId, Long requestId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        assertManage(teamId, resolved);
        TeamJoinRequestEntity request = request(teamId, requestId);
        if (request.getStatus() == TeamJoinRequestStatus.REJECTED) {
            return request.toRef();
        }
        requirePending(request);
        resolve(request, TeamJoinRequestStatus.REJECTED, resolved.requireUserId());
        return requestRepository.save(request).toRef();
    }

    private TeamJoinResult joinOpen(TeamEntity team, Long userId) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeamId(team.getTeamId());
        member.setUserId(userId);
        member.setRole(TeamRole.MEMBER);
        member.setStatus(TeamMemberStatus.ACTIVE);
        member.setCreatedBy(userId);
        member.setUpdatedBy(userId);
        TeamMemberRef memberRef = memberRepository.save(member).toRef();
        bumpPermissionVersion(team, userId);
        return new TeamJoinResult(TeamJoinOutcome.JOINED, memberRef, null);
    }

    private TeamJoinResult requestApproval(Long teamId, Long userId) {
        TeamJoinRequestEntity request = requestRepository.findByTeamIdAndUserId(teamId, userId).orElse(null);
        if (request != null && request.getStatus() == TeamJoinRequestStatus.PENDING) {
            return new TeamJoinResult(TeamJoinOutcome.PENDING, null, request.toRef());
        }
        if (request == null) {
            request = new TeamJoinRequestEntity();
            request.setTeamId(teamId);
            request.setUserId(userId);
        }
        request.setStatus(TeamJoinRequestStatus.PENDING);
        request.setRequestedAt(Instant.now());
        request.setResolvedAt(null);
        request.setResolvedBy(null);
        return new TeamJoinResult(TeamJoinOutcome.PENDING, null, requestRepository.save(request).toRef());
    }

    private TeamJoinRequestEntity request(Long teamId, Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new TeamValidationException("join requestId must be positive");
        }
        TeamJoinRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new TeamNotFoundException("Team join request not found: " + requestId));
        if (!teamId.equals(request.getTeamId())) {
            throw new TeamNotFoundException("Team join request not found: " + requestId);
        }
        return request;
    }

    private void requirePending(TeamJoinRequestEntity request) {
        if (request.getStatus() != TeamJoinRequestStatus.PENDING) {
            throw new TeamConflictException("Team join request is already resolved");
        }
    }

    private void resolve(TeamJoinRequestEntity request, TeamJoinRequestStatus status, Long actorUserId) {
        request.setStatus(status);
        request.setResolvedAt(Instant.now());
        request.setResolvedBy(actorUserId);
    }

    private void assertManage(Long teamId, TeamAccessContext actor) {
        if (!actor.platformAdmin()) {
            authorizationPort.assertGranted(teamId, actor.requireUserId(), TeamPermissionActions.MEMBER_MANAGE);
        }
    }

    private TeamEntity teamForUpdate(Long teamId) {
        return teamRepository.findForUpdate(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));
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
}
