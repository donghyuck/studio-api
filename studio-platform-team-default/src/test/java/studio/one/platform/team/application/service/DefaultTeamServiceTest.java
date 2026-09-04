package studio.one.platform.team.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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

import studio.one.platform.team.application.command.CreateTeamCommand;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.error.TeamConflictException;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamCompanyAssignmentPort;
import studio.one.platform.team.application.usecase.TeamWorkspaceProvisioningPort;
import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamRagReplyMode;
import studio.one.platform.team.domain.model.TeamRole;
import studio.one.platform.team.domain.model.TeamVisibility;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberJpaRepository;

@ExtendWith(MockitoExtension.class)
class DefaultTeamServiceTest {
    @Mock TeamJpaRepository teamRepository;
    @Mock TeamMemberJpaRepository memberRepository;
    @Mock TeamAuthorizationPort authorizationPort;
    @Mock TeamCompanyAssignmentPort companyAssignmentPort;
    @Mock TeamWorkspaceProvisioningPort workspaceProvisioningPort;

    private DefaultTeamService service;

    @BeforeEach
    void setUp() {
        service = new DefaultTeamService(
                teamRepository, memberRepository, authorizationPort, companyAssignmentPort, workspaceProvisioningPort);
        lenient().when(teamRepository.save(any())).thenAnswer(invocation -> {
            TeamEntity team = invocation.getArgument(0);
            team.setTeamId(10L);
            team.setCreatedAt(Instant.parse("2026-08-31T00:00:00Z"));
            team.setUpdatedAt(team.getCreatedAt());
            return team;
        });
        lenient().when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsCompanylessPublicTeamWithOwnerAndRootProvisioningHook() {
        var created = service.create(new CreateTeamCommand(
                null, "Public Knowledge", "public-knowledge", null,
                TeamVisibility.PUBLIC, TeamJoinPolicy.OPEN, true, TeamRagReplyMode.MENTION,
                new TeamAccessContext(7L, "owner", false)));

        assertThat(created.companyId()).isNull();
        assertThat(created.visibility()).isEqualTo(TeamVisibility.PUBLIC);
        assertThat(created.permissionVersion()).isEqualTo(1L);
        verify(companyAssignmentPort, never()).assertCanAssign(any(), any());
        verify(memberRepository).save(org.mockito.ArgumentMatchers.argThat(member ->
                member.getUserId().equals(7L)
                        && member.getRole() == TeamRole.OWNER
                        && member.getStatus() == TeamMemberStatus.ACTIVE));
        verify(workspaceProvisioningPort).createRootWorkspace(created, new TeamAccessContext(7L, "owner", false));
    }

    @Test
    void validatesOptionalCompanyAssignmentThroughPort() {
        service.create(new CreateTeamCommand(
                3L, "Company Team", "company-team", null,
                TeamVisibility.PRIVATE, TeamJoinPolicy.INVITE_ONLY, null, null,
                new TeamAccessContext(7L, "owner", false)));

        verify(companyAssignmentPort).assertCanAssign(3L, new TeamAccessContext(7L, "owner", false));
    }

    @Test
    void platformAdminCanCreateMigrationTargetWithoutProvisioningAnotherRoot() {
        var created = service.create(new CreateTeamCommand(
                null, "Migration Target", "migration-target", null,
                TeamVisibility.PRIVATE, TeamJoinPolicy.INVITE_ONLY, true, TeamRagReplyMode.MENTION,
                false, new TeamAccessContext(1L, "admin", true)));

        assertThat(created.teamId()).isEqualTo(10L);
        verify(workspaceProvisioningPort, never()).createRootWorkspace(any(), any());
    }

    @Test
    void nonAdminCannotCreateTeamWithoutRootWorkspace() {
        assertThatThrownBy(() -> service.create(new CreateTeamCommand(
                null, "Invalid Target", "invalid-target", null,
                TeamVisibility.PRIVATE, TeamJoinPolicy.INVITE_ONLY, true, TeamRagReplyMode.MENTION,
                false, new TeamAccessContext(7L, "member", false))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsDuplicateSlugBeforeCreatingOwner() {
        when(teamRepository.existsBySlugIgnoreCase("duplicate")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateTeamCommand(
                null, "Duplicate", "duplicate", null, null, null, null, null,
                new TeamAccessContext(7L, "owner", false))))
                .isInstanceOf(TeamConflictException.class);

        verify(teamRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }
}
