package studio.one.platform.team.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import studio.one.platform.team.application.command.DryRunTeamMigrationCommand;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.error.TeamConflictException;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamMigrationKnowledgePort;
import studio.one.platform.team.application.usecase.TeamMigrationWorkspacePort;
import studio.one.platform.team.application.usecase.TeamService;
import studio.one.platform.team.domain.model.TeamMigrationStatus;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationLockEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationLockJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationRunEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationRunJpaRepository;

@ExtendWith(MockitoExtension.class)
class DefaultTeamMigrationServiceTest {
    @Mock TeamMigrationRunJpaRepository runRepository;
    @Mock TeamMigrationLockJpaRepository lockRepository;
    @Mock TeamService teamService;
    @Mock TeamAuthorizationPort authorizationPort;
    @Mock TeamMigrationWorkspacePort workspacePort;
    @Mock TeamMigrationKnowledgePort knowledgePort;

    private DefaultTeamMigrationService service;
    private final TeamAccessContext admin = new TeamAccessContext(1L, "admin", true);

    @BeforeEach
    void setUp() {
        service = new DefaultTeamMigrationService(
                runRepository, lockRepository, teamService, authorizationPort, workspacePort, knowledgePort);
        org.mockito.Mockito.lenient().when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dryRunCapturesImmutableReferencesAndIsIdempotent() {
        when(runRepository.findByIdempotencyKey("migration-1")).thenReturn(Optional.empty());
        when(lockRepository.findById(any())).thenReturn(Optional.empty());
        when(workspacePort.captureBeforeSnapshot(List.of(11L)))
                .thenReturn(new TeamMigrationWorkspacePort.WorkspaceSnapshot("workspace-snapshot", 5, 3));
        when(knowledgePort.captureBeforeSnapshot(List.of(11L)))
                .thenReturn(new TeamMigrationKnowledgePort.KnowledgeSnapshot(
                        "knowledge-snapshot", 2, 1, 4, 20, "checksum"));

        var result = service.dryRun(new DryRunTeamMigrationCommand(
                "migration-1", 7L, List.of(11L), admin));

        assertThat(result.status()).isEqualTo(TeamMigrationStatus.DRY_RUN_COMPLETED);
        assertThat(result.beforeSnapshotReference()).isNotBlank();
        assertThat(result.workspaceSnapshotReference()).isEqualTo("workspace-snapshot");
        assertThat(result.knowledgeSnapshotReference()).isEqualTo("knowledge-snapshot");
        assertThat(result.vectorCount()).isEqualTo(20);
        verify(lockRepository).flush();
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingRunWithoutRecapturing() {
        TeamMigrationRunEntity existing = run(TeamMigrationStatus.DRY_RUN_COMPLETED);
        existing.setIdempotencyKey("same-key");
        when(runRepository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        var result = service.dryRun(new DryRunTeamMigrationCommand(
                "same-key", 7L, List.of(11L), admin));

        assertThat(result.runId()).isEqualTo(existing.getRunId());
        verify(workspacePort, never()).captureBeforeSnapshot(any());
        verify(knowledgePort, never()).captureBeforeSnapshot(any());
    }

    @Test
    void overlappingSourceRootIsRejectedBySingleFlightLock() {
        when(runRepository.findByIdempotencyKey("locked")).thenReturn(Optional.empty());
        TeamMigrationLockEntity lock = new TeamMigrationLockEntity();
        lock.setLockKey("root:11");
        lock.setRunId(UUID.randomUUID().toString());
        when(lockRepository.findById("root:11")).thenReturn(Optional.of(lock));

        assertThatThrownBy(() -> service.dryRun(new DryRunTeamMigrationCommand(
                "locked", 7L, List.of(11L), admin)))
                .isInstanceOf(TeamConflictException.class)
                .hasMessageContaining("already locked");
    }

    @Test
    void applyVerifyAndRollbackUseExistingDataPortsWithoutDeletionContract() {
        TeamMigrationRunEntity run = run(TeamMigrationStatus.DRY_RUN_COMPLETED);
        when(runRepository.findForUpdate(run.getRunId())).thenReturn(Optional.of(run));
        when(workspacePort.verifyAssignment(any(), any(), any(), any()))
                .thenReturn(new TeamMigrationWorkspacePort.MigrationVerification(true, "ok"));
        when(knowledgePort.verifyExistingKnowledge(any(), any(), any(), any()))
                .thenReturn(new TeamMigrationKnowledgePort.MigrationVerification(true, "ok"));

        assertThat(service.apply(run.getRunId(), admin).status()).isEqualTo(TeamMigrationStatus.RUNNING);
        assertThat(service.verify(run.getRunId(), admin).status()).isEqualTo(TeamMigrationStatus.CUTOVER_COMPLETED);
        assertThat(service.rollback(run.getRunId(), admin).status()).isEqualTo(TeamMigrationStatus.ROLLED_BACK);

        verify(workspacePort).assignToTeam(
                run.getRunId(), 7L, List.of(11L), "workspace-snapshot");
        verify(knowledgePort).attachExistingKnowledge(
                run.getRunId(), 7L, List.of(11L), "knowledge-snapshot");
        verify(knowledgePort).rollbackGeneratedState(run.getRunId(), 7L, "knowledge-snapshot");
        verify(workspacePort).rollbackAssignment(
                run.getRunId(), 7L, List.of(11L), "workspace-snapshot");
        verify(lockRepository, atLeastOnce()).deleteByRunId(run.getRunId());
    }

    private TeamMigrationRunEntity run(TeamMigrationStatus status) {
        TeamMigrationRunEntity run = new TeamMigrationRunEntity();
        run.setRunId(UUID.randomUUID().toString());
        run.setIdempotencyKey("key-" + run.getRunId());
        run.setTargetTeamId(7L);
        run.setSourceRootIds("11");
        run.setStatus(status);
        run.setBeforeSnapshotReference("before");
        run.setWorkspaceSnapshotReference("workspace-snapshot");
        run.setKnowledgeSnapshotReference("knowledge-snapshot");
        run.setCreatedBy(1L);
        run.setUpdatedBy(1L);
        return run;
    }
}
