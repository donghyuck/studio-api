package studio.one.platform.team.application.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import studio.one.platform.team.application.command.DryRunTeamMigrationCommand;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.error.TeamConflictException;
import studio.one.platform.team.application.error.TeamNotFoundException;
import studio.one.platform.team.application.error.TeamValidationException;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamMigrationKnowledgePort;
import studio.one.platform.team.application.usecase.TeamMigrationService;
import studio.one.platform.team.application.usecase.TeamMigrationWorkspacePort;
import studio.one.platform.team.application.usecase.TeamService;
import studio.one.platform.team.domain.model.TeamMigrationRef;
import studio.one.platform.team.domain.model.TeamMigrationStatus;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationLockEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationLockJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationRunEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationRunJpaRepository;

public class DefaultTeamMigrationService implements TeamMigrationService {
    private final TeamMigrationRunJpaRepository runRepository;
    private final TeamMigrationLockJpaRepository lockRepository;
    private final TeamService teamService;
    private final TeamAuthorizationPort authorizationPort;
    private final TeamMigrationWorkspacePort workspacePort;
    private final TeamMigrationKnowledgePort knowledgePort;

    public DefaultTeamMigrationService(
            TeamMigrationRunJpaRepository runRepository,
            TeamMigrationLockJpaRepository lockRepository,
            TeamService teamService,
            TeamAuthorizationPort authorizationPort,
            TeamMigrationWorkspacePort workspacePort,
            TeamMigrationKnowledgePort knowledgePort) {
        this.runRepository = runRepository;
        this.lockRepository = lockRepository;
        this.teamService = teamService;
        this.authorizationPort = authorizationPort;
        this.workspacePort = workspacePort;
        this.knowledgePort = knowledgePort;
    }

    @Override
    @Transactional
    public TeamMigrationRef dryRun(DryRunTeamMigrationCommand command) {
        TeamAccessContext actor = requireActor(command.actor());
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        Long targetTeamId = requirePositive(command.targetTeamId(), "targetTeamId");
        List<Long> roots = normalizeRoots(command.sourceRootWorkspaceIds());
        assertTargetAccess(targetTeamId, actor);

        TeamMigrationRunEntity existing = runRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            assertSameRequest(existing, targetTeamId, roots);
            return existing.toRef();
        }

        TeamMigrationRunEntity run = new TeamMigrationRunEntity();
        run.setRunId(UUID.randomUUID().toString());
        run.setIdempotencyKey(idempotencyKey);
        run.setTargetTeamId(targetTeamId);
        run.setSourceRootIds(joinRoots(roots));
        run.setStatus(TeamMigrationStatus.DRAFT);
        run.setCreatedBy(actor.requireUserId());
        run.setUpdatedBy(actor.requireUserId());
        run = runRepository.save(run);
        acquireLocks(run.getRunId(), targetTeamId, roots);

        try {
            TeamMigrationWorkspacePort.WorkspaceSnapshot workspace = workspacePort.captureBeforeSnapshot(roots);
            TeamMigrationKnowledgePort.KnowledgeSnapshot knowledge = knowledgePort.captureBeforeSnapshot(roots);
            assertSnapshot(workspace == null ? null : workspace.reference(), "workspace");
            assertSnapshot(knowledge == null ? null : knowledge.reference(), "knowledge");
            run.setWorkspaceSnapshotReference(workspace.reference());
            run.setKnowledgeSnapshotReference(knowledge.reference());
            run.setBeforeSnapshotReference(snapshotReference(workspace.reference(), knowledge.reference()));
            run.setWorkspaceCount(workspace.workspaceCount());
            run.setMemberCount(workspace.memberCount());
            run.setAttachmentCount(knowledge.attachmentCount());
            run.setWikiCount(knowledge.wikiCount());
            run.setWebSourceCount(knowledge.webSourceCount());
            run.setVectorCount(knowledge.vectorCount());
            run.setSourceChecksum(knowledge.sourceChecksum());
            transition(run, TeamMigrationStatus.DRY_RUN_COMPLETED);
        } catch (RuntimeException ex) {
            fail(run, TeamMigrationStatus.FAILED, ex);
            lockRepository.deleteByRunId(run.getRunId());
        }
        run.setUpdatedBy(actor.requireUserId());
        return runRepository.save(run).toRef();
    }

    @Override
    @Transactional
    public TeamMigrationRef apply(String runId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        TeamMigrationRunEntity run = runForUpdate(runId);
        assertTargetAccess(run.getTargetTeamId(), resolved);
        if (run.getStatus() == TeamMigrationStatus.RUNNING
                || run.getStatus() == TeamMigrationStatus.VERIFYING
                || run.getStatus() == TeamMigrationStatus.VERIFIED
                || run.getStatus() == TeamMigrationStatus.CUTOVER_COMPLETED) {
            return run.toRef();
        }
        requireStatus(run, TeamMigrationStatus.DRY_RUN_COMPLETED);
        transition(run, TeamMigrationStatus.READY);
        transition(run, TeamMigrationStatus.RUNNING);
        run.setUpdatedBy(resolved.requireUserId());
        runRepository.save(run);
        try {
            workspacePort.assignToTeam(
                    run.getRunId(), run.getTargetTeamId(), run.sourceRootWorkspaceIds(), run.getWorkspaceSnapshotReference());
            knowledgePort.attachExistingKnowledge(
                    run.getRunId(), run.getTargetTeamId(), run.sourceRootWorkspaceIds(), run.getKnowledgeSnapshotReference());
        } catch (RuntimeException ex) {
            fail(run, TeamMigrationStatus.ROLLBACK_REQUIRED, ex);
        }
        return runRepository.save(run).toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public TeamMigrationRef get(String runId, TeamAccessContext actor) {
        TeamMigrationRunEntity run = run(runId);
        assertTargetAccess(run.getTargetTeamId(), requireActor(actor));
        return run.toRef();
    }

    @Override
    @Transactional
    public TeamMigrationRef verify(String runId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        TeamMigrationRunEntity run = runForUpdate(runId);
        assertTargetAccess(run.getTargetTeamId(), resolved);
        if (run.getStatus() == TeamMigrationStatus.CUTOVER_COMPLETED) {
            return run.toRef();
        }
        requireStatus(run, TeamMigrationStatus.RUNNING);
        transition(run, TeamMigrationStatus.VERIFYING);
        try {
            var workspace = workspacePort.verifyAssignment(
                    run.getRunId(), run.getTargetTeamId(), run.sourceRootWorkspaceIds(), run.getWorkspaceSnapshotReference());
            var knowledge = knowledgePort.verifyExistingKnowledge(
                    run.getRunId(), run.getTargetTeamId(), run.sourceRootWorkspaceIds(), run.getKnowledgeSnapshotReference());
            if (workspace != null && workspace.matches() && knowledge != null && knowledge.matches()) {
                transition(run, TeamMigrationStatus.VERIFIED);
                transition(run, TeamMigrationStatus.CUTOVER_COMPLETED);
                run.setFailureMessage(null);
                lockRepository.deleteByRunId(run.getRunId());
            } else {
                run.setFailureMessage("Migration verification mismatch");
                transition(run, TeamMigrationStatus.ROLLBACK_REQUIRED);
            }
        } catch (RuntimeException ex) {
            fail(run, TeamMigrationStatus.ROLLBACK_REQUIRED, ex);
        }
        run.setUpdatedBy(resolved.requireUserId());
        return runRepository.save(run).toRef();
    }

    @Override
    @Transactional
    public TeamMigrationRef rollback(String runId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        TeamMigrationRunEntity run = runForUpdate(runId);
        assertTargetAccess(run.getTargetTeamId(), resolved);
        if (run.getStatus() == TeamMigrationStatus.ROLLED_BACK) {
            return run.toRef();
        }
        boolean mayHaveMutated = switch (run.getStatus()) {
            case RUNNING, VERIFYING, VERIFIED, CUTOVER_COMPLETED, ROLLBACK_REQUIRED -> true;
            default -> false;
        };
        if (mayHaveMutated && run.getStatus() != TeamMigrationStatus.ROLLBACK_REQUIRED) {
            transition(run, TeamMigrationStatus.ROLLBACK_REQUIRED);
        }
        try {
            if (mayHaveMutated) {
                knowledgePort.rollbackGeneratedState(
                        run.getRunId(), run.getTargetTeamId(), run.getKnowledgeSnapshotReference());
                workspacePort.rollbackAssignment(
                        run.getRunId(), run.getTargetTeamId(), run.sourceRootWorkspaceIds(), run.getWorkspaceSnapshotReference());
            }
            transition(run, TeamMigrationStatus.ROLLED_BACK);
            run.setFailureMessage(null);
            lockRepository.deleteByRunId(run.getRunId());
        } catch (RuntimeException ex) {
            fail(run, TeamMigrationStatus.FAILED, ex);
        }
        run.setUpdatedBy(resolved.requireUserId());
        return runRepository.save(run).toRef();
    }

    private void acquireLocks(String runId, Long targetTeamId, List<Long> roots) {
        List<String> keys = new ArrayList<>();
        keys.add("team:" + targetTeamId);
        roots.forEach(root -> keys.add("root:" + root));
        keys.sort(Comparator.naturalOrder());
        for (String key : keys) {
            TeamMigrationLockEntity existing = lockRepository.findById(key).orElse(null);
            if (existing != null && !runId.equals(existing.getRunId())) {
                throw new TeamConflictException("Team migration scope is already locked: " + key);
            }
            if (existing == null) {
                TeamMigrationLockEntity lock = new TeamMigrationLockEntity();
                lock.setLockKey(key);
                lock.setRunId(runId);
                lockRepository.save(lock);
            }
        }
        lockRepository.flush();
    }

    private void assertTargetAccess(Long targetTeamId, TeamAccessContext actor) {
        teamService.get(targetTeamId, actor);
        if (!actor.platformAdmin()) {
            authorizationPort.assertGranted(targetTeamId, actor.requireUserId(), TeamPermissionActions.UPDATE);
        }
    }

    private void assertSameRequest(TeamMigrationRunEntity run, Long targetTeamId, List<Long> roots) {
        if (!targetTeamId.equals(run.getTargetTeamId()) || !roots.equals(run.sourceRootWorkspaceIds())) {
            throw new TeamConflictException("Idempotency key was already used for another migration request");
        }
    }

    private void transition(TeamMigrationRunEntity run, TeamMigrationStatus target) {
        if (!run.getStatus().canTransitionTo(target)) {
            throw new TeamConflictException("Invalid Team migration transition: " + run.getStatus() + " -> " + target);
        }
        run.setStatus(target);
    }

    private void requireStatus(TeamMigrationRunEntity run, TeamMigrationStatus expected) {
        if (run.getStatus() != expected) {
            throw new TeamConflictException(
                    "Team migration status must be " + expected + " but was " + run.getStatus());
        }
    }

    private void fail(TeamMigrationRunEntity run, TeamMigrationStatus failureStatus, RuntimeException ex) {
        if (run.getStatus().canTransitionTo(failureStatus)) {
            run.setStatus(failureStatus);
        }
        run.setFailureMessage("Migration operation failed: " + ex.getClass().getSimpleName());
    }

    private TeamMigrationRunEntity run(String runId) {
        return runRepository.findById(normalizeRunId(runId))
                .orElseThrow(() -> new TeamNotFoundException("Team migration run not found: " + runId));
    }

    private TeamMigrationRunEntity runForUpdate(String runId) {
        return runRepository.findForUpdate(normalizeRunId(runId))
                .orElseThrow(() -> new TeamNotFoundException("Team migration run not found: " + runId));
    }

    private String normalizeRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            throw new TeamValidationException("migration runId is required");
        }
        try {
            return UUID.fromString(runId.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new TeamValidationException("migration runId must be a UUID");
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new TeamValidationException("migration idempotencyKey is required");
        }
        String normalized = key.trim();
        if (normalized.length() > 128) {
            throw new TeamValidationException("migration idempotencyKey is too long");
        }
        return normalized;
    }

    private List<Long> normalizeRoots(List<Long> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new TeamValidationException("sourceRootWorkspaceIds is required");
        }
        List<Long> normalized = roots.stream()
                .map(root -> requirePositive(root, "sourceRootWorkspaceId"))
                .distinct()
                .sorted()
                .toList();
        if (normalized.size() != roots.size()) {
            throw new TeamValidationException("sourceRootWorkspaceIds must not contain duplicates");
        }
        return normalized;
    }

    private Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new TeamValidationException(field + " must be positive");
        }
        return value;
    }

    private TeamAccessContext requireActor(TeamAccessContext actor) {
        if (actor == null) {
            throw new TeamValidationException("team migration actor is required");
        }
        actor.requireUserId();
        if (!actor.platformAdmin()) {
            throw new AccessDeniedException("Team migration requires a platform administrator");
        }
        return actor;
    }

    private String joinRoots(List<Long> roots) {
        return roots.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private void assertSnapshot(String reference, String type) {
        if (!StringUtils.hasText(reference)) {
            throw new TeamValidationException(type + " before snapshot reference is required");
        }
    }

    private String snapshotReference(String workspaceReference, String knowledgeReference) {
        return UUID.nameUUIDFromBytes(
                (workspaceReference + "|" + knowledgeReference).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
