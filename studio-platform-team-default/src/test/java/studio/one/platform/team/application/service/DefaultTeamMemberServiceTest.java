package studio.one.platform.team.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.command.TeamMemberCommand;
import studio.one.platform.team.application.error.TeamConflictException;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.team.domain.model.TeamRole;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberJpaRepository;

@ExtendWith(MockitoExtension.class)
class DefaultTeamMemberServiceTest {
    @Mock TeamJpaRepository teamRepository;
    @Mock TeamMemberJpaRepository memberRepository;
    @Mock TeamAuthorizationPort authorizationPort;

    private DefaultTeamMemberService service;
    private TeamEntity team;

    @BeforeEach
    void setUp() {
        service = new DefaultTeamMemberService(teamRepository, memberRepository, authorizationPort);
        team = new TeamEntity();
        team.setTeamId(1L);
        team.setPermissionVersion(4L);
        team.setUpdatedBy(10L);
        when(teamRepository.findForUpdate(1L)).thenReturn(Optional.of(team));
    }

    @Test
    void memberAdditionAdvancesPermissionVersion() {
        when(authorizationPort.findEffectiveRole(1L, 10L)).thenReturn(Optional.of(TeamRole.ADMIN));
        when(memberRepository.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.empty());
        when(memberRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var added = service.addMember(1L, new TeamMemberCommand(
                20L, TeamRole.MEMBER, new TeamAccessContext(10L, "admin", false)));

        assertThat(added.userId()).isEqualTo(20L);
        assertThat(team.getPermissionVersion()).isEqualTo(5L);
        verify(authorizationPort).assertGranted(1L, 10L, TeamPermissionActions.MEMBER_MANAGE);
        verify(teamRepository).save(team);
    }

    @Test
    void lastOwnerCannotBeRemoved() {
        TeamMemberEntity owner = new TeamMemberEntity();
        owner.setTeamId(1L);
        owner.setUserId(10L);
        owner.setRole(TeamRole.OWNER);
        owner.setStatus(TeamMemberStatus.ACTIVE);
        when(memberRepository.findByTeamIdAndUserIdAndStatus(1L, 10L, TeamMemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));
        when(memberRepository.countByTeamIdAndRoleAndStatus(1L, TeamRole.OWNER, TeamMemberStatus.ACTIVE))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.removeMember(
                1L, 10L, new TeamAccessContext(10L, "owner", false)))
                .isInstanceOf(TeamConflictException.class)
                .hasMessageContaining("at least one active owner");
    }

    @Test
    void adminCannotAssignOwnerRole() {
        when(authorizationPort.findEffectiveRole(1L, 10L)).thenReturn(Optional.of(TeamRole.ADMIN));

        assertThatThrownBy(() -> service.addMember(1L, new TeamMemberCommand(
                20L, TeamRole.OWNER, new TeamAccessContext(10L, "admin", false))))
                .isInstanceOf(AccessDeniedException.class);
    }
}
