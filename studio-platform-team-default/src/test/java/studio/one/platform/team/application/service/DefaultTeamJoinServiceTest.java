package studio.one.platform.team.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.domain.model.TeamJoinOutcome;
import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamJoinRequestStatus;
import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamRole;
import studio.one.platform.team.domain.model.TeamStatus;
import studio.one.platform.team.domain.model.TeamVisibility;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJoinRequestEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJoinRequestJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberJpaRepository;

@ExtendWith(MockitoExtension.class)
class DefaultTeamJoinServiceTest {
    @Mock TeamJpaRepository teamRepository;
    @Mock TeamMemberJpaRepository memberRepository;
    @Mock TeamJoinRequestJpaRepository requestRepository;
    @Mock TeamAuthorizationPort authorizationPort;

    private DefaultTeamJoinService service;
    private TeamEntity team;

    @BeforeEach
    void setUp() {
        service = new DefaultTeamJoinService(
                teamRepository, memberRepository, requestRepository, authorizationPort);
        team = new TeamEntity();
        team.setTeamId(7L);
        team.setStatus(TeamStatus.ACTIVE);
        team.setVisibility(TeamVisibility.PUBLIC);
        team.setPermissionVersion(3L);
        team.setUpdatedBy(1L);
        when(teamRepository.findForUpdate(7L)).thenReturn(Optional.of(team));
        org.mockito.Mockito.lenient().when(teamRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(requestRepository.save(any())).thenAnswer(invocation -> {
            TeamJoinRequestEntity request = invocation.getArgument(0);
            if (request.getRequestId() == null) {
                request.setRequestId(99L);
            }
            if (request.getRequestedAt() == null) {
                request.setRequestedAt(Instant.parse("2026-08-31T00:00:00Z"));
            }
            return request;
        });
    }

    @Test
    void openPolicyAddsActiveMemberAndAdvancesPermissionVersion() {
        team.setJoinPolicy(TeamJoinPolicy.OPEN);
        when(memberRepository.findByTeamIdAndUserIdAndStatus(7L, 20L, TeamMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        var result = service.join(7L, new TeamAccessContext(20L, "member", false));

        assertThat(result.outcome()).isEqualTo(TeamJoinOutcome.JOINED);
        assertThat(result.member().role()).isEqualTo(TeamRole.MEMBER);
        assertThat(team.getPermissionVersion()).isEqualTo(4L);
    }

    @Test
    void approvalPolicyReusesOnePendingRequest() {
        team.setJoinPolicy(TeamJoinPolicy.APPROVAL);
        when(memberRepository.findByTeamIdAndUserIdAndStatus(7L, 20L, TeamMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(requestRepository.findByTeamIdAndUserId(7L, 20L)).thenReturn(Optional.empty());

        var first = service.join(7L, new TeamAccessContext(20L, "member", false));
        assertThat(first.outcome()).isEqualTo(TeamJoinOutcome.PENDING);

        when(requestRepository.findByTeamIdAndUserId(7L, 20L))
                .thenReturn(Optional.of(request(TeamJoinRequestStatus.PENDING)));
        var repeated = service.join(7L, new TeamAccessContext(20L, "member", false));
        assertThat(repeated.outcome()).isEqualTo(TeamJoinOutcome.PENDING);
        assertThat(repeated.request().requestId()).isEqualTo(99L);
    }

    @Test
    void inviteOnlyPolicyRejectsSelfJoin() {
        team.setJoinPolicy(TeamJoinPolicy.INVITE_ONLY);
        when(memberRepository.findByTeamIdAndUserIdAndStatus(7L, 20L, TeamMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(7L, new TeamAccessContext(20L, "member", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void privateTeamRejectsSelfJoinEvenWhenPolicyIsOpen() {
        team.setVisibility(TeamVisibility.PRIVATE);
        team.setJoinPolicy(TeamJoinPolicy.OPEN);
        when(memberRepository.findByTeamIdAndUserIdAndStatus(7L, 20L, TeamMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(7L, new TeamAccessContext(20L, "member", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unlistedTeamRejectsSelfJoinEvenWhenApprovalIsConfigured() {
        team.setVisibility(TeamVisibility.UNLISTED);
        team.setJoinPolicy(TeamJoinPolicy.APPROVAL);
        when(memberRepository.findByTeamIdAndUserIdAndStatus(7L, 20L, TeamMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(7L, new TeamAccessContext(20L, "member", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void approvalCreatesMemberAndAdvancesPermissionVersion() {
        TeamJoinRequestEntity pending = request(TeamJoinRequestStatus.PENDING);
        when(requestRepository.findById(99L)).thenReturn(Optional.of(pending));
        when(memberRepository.findByTeamIdAndUserId(7L, 20L)).thenReturn(Optional.empty());

        var approved = service.approve(7L, 99L, new TeamAccessContext(1L, "owner", false));

        assertThat(approved.status()).isEqualTo(TeamJoinRequestStatus.APPROVED);
        assertThat(approved.resolvedBy()).isEqualTo(1L);
        assertThat(team.getPermissionVersion()).isEqualTo(4L);
        verify(authorizationPort).assertGranted(7L, 1L, "team.member.manage");
    }

    private TeamJoinRequestEntity request(TeamJoinRequestStatus status) {
        TeamJoinRequestEntity request = new TeamJoinRequestEntity();
        request.setRequestId(99L);
        request.setTeamId(7L);
        request.setUserId(20L);
        request.setStatus(status);
        request.setRequestedAt(Instant.parse("2026-08-31T00:00:00Z"));
        return request;
    }
}
