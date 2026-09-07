package studio.one.platform.workspace.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.platform.team.application.usecase.TeamMigrationWorkspacePort;
import studio.one.platform.workspace.application.error.WorkspaceConflictException;
import studio.one.platform.workspace.application.error.WorkspaceNotFoundException;
import studio.one.platform.workspace.application.error.WorkspaceValidationException;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceEntity;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceMemberJpaRepository;

/**
 * Reassigns one or more independent Workspace trees to a Team without changing
 * Workspace identity, paths, hierarchy, or closure rows.
 */
@RequiredArgsConstructor
public class DefaultTeamMigrationWorkspaceAdapter implements TeamMigrationWorkspacePort {

    private static final String REFERENCE_PREFIX = "workspace-v2";
    private static final int MAX_WORKSPACES = 10_000;
    private static final int MAX_REFERENCE_LENGTH = 2_000_000;

    private final WorkspaceJpaRepository workspaceRepository;
    private final WorkspaceClosureJpaRepository closureRepository;
    private final WorkspaceMemberJpaRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public WorkspaceSnapshot captureBeforeSnapshot(List<Long> sourceRootWorkspaceIds) {
        List<Long> roots = normalizeRoots(sourceRootWorkspaceIds);
        List<WorkspaceEntity> forest = loadForest(roots);
        Snapshot snapshot = Snapshot.from(roots, forest);
        long memberCount = memberRepository.findByWorkspaceIdIn(snapshot.workspaceIds()).size();
        return new WorkspaceSnapshot(encode(snapshot), forest.size(), memberCount);
    }

    @Override
    @Transactional
    public void assignToTeam(
            String runId,
            Long targetTeamId,
            List<Long> sourceRootWorkspaceIds,
            String snapshotReference) {
        requireRunId(runId);
        Long teamId = requireTeamId(targetTeamId);
        Snapshot snapshot = decodeAndValidate(sourceRootWorkspaceIds, snapshotReference);
        List<WorkspaceEntity> forest = loadForest(snapshot.rootIds());
        assertExactForest(snapshot, forest);
        assertNoTargetPathConflicts(snapshot, teamId);

        for (WorkspaceEntity workspace : forest) {
            WorkspaceState before = snapshot.state(workspace.getWorkspaceId());
            assertStructureUnchanged(before, workspace);
            if (!ownerMatchesBefore(before, workspace) && !ownerMatchesTarget(teamId, workspace)) {
                throw new WorkspaceConflictException(
                        "Workspace ownership changed after migration snapshot: " + workspace.getWorkspaceId());
            }
            workspace.setTeamId(teamId);
            workspace.setCompanyId(null);
        }
        workspaceRepository.saveAll(forest);
        workspaceRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public MigrationVerification verifyAssignment(
            String runId,
            Long targetTeamId,
            List<Long> sourceRootWorkspaceIds,
            String snapshotReference) {
        requireRunId(runId);
        Long teamId = requireTeamId(targetTeamId);
        Snapshot snapshot = decodeAndValidate(sourceRootWorkspaceIds, snapshotReference);
        List<WorkspaceEntity> forest = loadForest(snapshot.rootIds());
        if (!sameWorkspaceIds(snapshot, forest)) {
            return new MigrationVerification(false, "Workspace forest IDs or count changed");
        }
        for (WorkspaceEntity workspace : forest) {
            WorkspaceState before = snapshot.state(workspace.getWorkspaceId());
            if (!structureMatches(before, workspace) || !ownerMatchesTarget(teamId, workspace)) {
                return new MigrationVerification(false,
                        "Workspace assignment mismatch: " + workspace.getWorkspaceId());
            }
        }
        return new MigrationVerification(true,
                "Workspace forest assignment verified: roots=" + snapshot.rootIds().size()
                        + ", workspaces=" + forest.size());
    }

    @Override
    @Transactional
    public void rollbackAssignment(
            String runId,
            Long targetTeamId,
            List<Long> sourceRootWorkspaceIds,
            String snapshotReference) {
        requireRunId(runId);
        Long teamId = requireTeamId(targetTeamId);
        Snapshot snapshot = decodeAndValidate(sourceRootWorkspaceIds, snapshotReference);
        List<WorkspaceEntity> forest = loadForest(snapshot.rootIds());
        assertExactForest(snapshot, forest);

        for (WorkspaceEntity workspace : forest) {
            WorkspaceState before = snapshot.state(workspace.getWorkspaceId());
            assertStructureUnchanged(before, workspace);
            if (!ownerMatchesTarget(teamId, workspace) && !ownerMatchesBefore(before, workspace)) {
                throw new WorkspaceConflictException(
                        "Workspace ownership cannot be safely rolled back: " + workspace.getWorkspaceId());
            }
            workspace.setTeamId(before.teamId());
            workspace.setCompanyId(before.companyId());
        }
        workspaceRepository.saveAll(forest);
        workspaceRepository.flush();
    }

    private List<WorkspaceEntity> loadForest(List<Long> rootIds) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<WorkspaceEntity> forest = new ArrayList<>();
        for (Long rootId : rootIds) {
            for (WorkspaceEntity workspace : loadSubtree(rootId)) {
                if (!seen.add(workspace.getWorkspaceId())) {
                    throw new WorkspaceValidationException(
                            "Workspace migration roots contain overlapping subtrees");
                }
                forest.add(workspace);
                if (forest.size() > MAX_WORKSPACES) {
                    throw new WorkspaceValidationException(
                            "Workspace migration exceeds maximum forest size: " + MAX_WORKSPACES);
                }
            }
        }
        return forest.stream().sorted(Comparator.comparing(WorkspaceEntity::getWorkspaceId)).toList();
    }

    private List<WorkspaceEntity> loadSubtree(Long rootId) {
        WorkspaceEntity root = workspaceRepository.findById(rootId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace root not found: " + rootId));
        if (root.getParentId() != null || !rootId.equals(root.getRootId())) {
            throw new WorkspaceValidationException("Migration source must be a root Workspace: " + rootId);
        }
        List<Long> ids = closureRepository.findDescendantIds(rootId);
        if (ids.isEmpty() || !ids.contains(rootId)) {
            throw new WorkspaceValidationException("Workspace closure is incomplete for root: " + rootId);
        }
        List<WorkspaceEntity> subtree = workspaceRepository.findByWorkspaceIdIn(ids).stream()
                .sorted(Comparator.comparing(WorkspaceEntity::getWorkspaceId))
                .toList();
        if (subtree.size() != ids.size()) {
            throw new WorkspaceValidationException("Workspace closure references missing rows");
        }
        for (WorkspaceEntity workspace : subtree) {
            if (!rootId.equals(workspace.getRootId())) {
                throw new WorkspaceValidationException(
                        "Workspace subtree contains a different root: " + workspace.getWorkspaceId());
            }
        }
        return subtree;
    }

    private Snapshot decodeAndValidate(List<Long> roots, String reference) {
        List<Long> normalizedRoots = normalizeRoots(roots);
        Snapshot snapshot = decode(reference);
        if (!normalizedRoots.equals(snapshot.rootIds())) {
            throw new WorkspaceValidationException("Workspace snapshot roots do not match migration request");
        }
        return snapshot;
    }

    private List<Long> normalizeRoots(List<Long> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new WorkspaceValidationException("Workspace migration source roots are required");
        }
        if (roots.stream().anyMatch(root -> root == null || root <= 0)) {
            throw new WorkspaceValidationException("Workspace migration source roots must be positive");
        }
        List<Long> normalized = roots.stream().sorted().toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new WorkspaceValidationException("Workspace migration source roots must not contain duplicates");
        }
        return normalized;
    }

    private Long requireTeamId(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new WorkspaceValidationException("Workspace migration targetTeamId must be positive");
        }
        return teamId;
    }

    private void requireRunId(String runId) {
        if (!StringUtils.hasText(runId) || runId.length() > 100) {
            throw new WorkspaceValidationException("Workspace migration runId is invalid");
        }
    }

    private void assertNoTargetPathConflicts(Snapshot snapshot, Long teamId) {
        Set<Long> migratingIds = Set.copyOf(snapshot.workspaceIds());
        for (WorkspaceState state : snapshot.states()) {
            workspaceRepository.findByTeamIdAndPath(teamId, state.path()).ifPresent(existing -> {
                if (!migratingIds.contains(existing.getWorkspaceId())) {
                    throw new WorkspaceConflictException(
                            "Target Team already contains Workspace path: " + state.path());
                }
            });
        }
    }

    private void assertExactForest(Snapshot snapshot, List<WorkspaceEntity> forest) {
        if (!sameWorkspaceIds(snapshot, forest)) {
            throw new WorkspaceConflictException("Workspace forest changed after migration snapshot");
        }
    }

    private boolean sameWorkspaceIds(Snapshot snapshot, List<WorkspaceEntity> forest) {
        return snapshot.workspaceIds().equals(forest.stream()
                .map(WorkspaceEntity::getWorkspaceId)
                .sorted()
                .toList());
    }

    private void assertStructureUnchanged(WorkspaceState before, WorkspaceEntity current) {
        if (!structureMatches(before, current)) {
            throw new WorkspaceConflictException(
                    "Workspace structure changed after migration snapshot: " + current.getWorkspaceId());
        }
    }

    private boolean structureMatches(WorkspaceState before, WorkspaceEntity current) {
        return same(before.parentId(), current.getParentId())
                && same(before.rootId(), current.getRootId())
                && before.path().equals(current.getPath());
    }

    private boolean ownerMatchesBefore(WorkspaceState before, WorkspaceEntity current) {
        return same(before.teamId(), current.getTeamId())
                && same(before.companyId(), current.getCompanyId());
    }

    private boolean ownerMatchesTarget(Long teamId, WorkspaceEntity current) {
        return teamId.equals(current.getTeamId()) && current.getCompanyId() == null;
    }

    private boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private String encode(Snapshot snapshot) {
        StringBuilder payload = new StringBuilder("roots=")
                .append(snapshot.rootIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
                .append('\n');
        for (WorkspaceState state : snapshot.states()) {
            payload.append(state.workspaceId()).append('|')
                    .append(nullable(state.teamId())).append('|')
                    .append(nullable(state.companyId())).append('|')
                    .append(nullable(state.parentId())).append('|')
                    .append(nullable(state.rootId())).append('|')
                    .append(Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(state.path().getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
        }
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        String reference = REFERENCE_PREFIX + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "."
                + digest(bytes);
        if (reference.length() > MAX_REFERENCE_LENGTH) {
            throw new WorkspaceValidationException("Workspace migration snapshot is too large");
        }
        return reference;
    }

    private Snapshot decode(String reference) {
        if (!StringUtils.hasText(reference) || reference.length() > MAX_REFERENCE_LENGTH) {
            throw new WorkspaceValidationException("Workspace migration snapshot reference is invalid");
        }
        String[] parts = reference.split("\\.", -1);
        if (parts.length != 3 || !REFERENCE_PREFIX.equals(parts[0])) {
            throw new WorkspaceValidationException("Unsupported Workspace migration snapshot reference");
        }
        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new WorkspaceValidationException("Workspace migration snapshot is malformed");
        }
        if (!digest(payload).equals(parts[2])) {
            throw new WorkspaceValidationException("Workspace migration snapshot checksum mismatch");
        }
        String[] lines = new String(payload, StandardCharsets.UTF_8).split("\\n");
        if (lines.length < 2 || !lines[0].startsWith("roots=")) {
            throw new WorkspaceValidationException("Workspace migration snapshot payload is incomplete");
        }
        List<Long> rootIds = normalizeRoots(java.util.Arrays.stream(lines[0].substring("roots=".length()).split(","))
                .map(value -> parsePositive(value, "rootId"))
                .toList());
        List<WorkspaceState> states = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        Set<String> seenPaths = new HashSet<>();
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split("\\|", -1);
            if (values.length != 6) {
                throw new WorkspaceValidationException("Workspace migration snapshot row is malformed");
            }
            Long workspaceId = parsePositive(values[0], "workspaceId");
            if (!seenIds.add(workspaceId)) {
                throw new WorkspaceValidationException("Workspace migration snapshot contains duplicate IDs");
            }
            String path;
            try {
                path = new String(Base64.getUrlDecoder().decode(values[5]), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                throw new WorkspaceValidationException("Workspace migration snapshot path is malformed");
            }
            if (!StringUtils.hasText(path) || !seenPaths.add(path)) {
                throw new WorkspaceValidationException("Workspace migration snapshot path is empty or duplicated");
            }
            states.add(new WorkspaceState(
                    workspaceId,
                    parseNullable(values[1]),
                    parseNullable(values[2]),
                    parseNullable(values[3]),
                    parseNullable(values[4]),
                    path));
        }
        if (states.isEmpty() || states.size() > MAX_WORKSPACES || !seenIds.containsAll(rootIds)) {
            throw new WorkspaceValidationException("Workspace migration snapshot size or roots are invalid");
        }
        states.sort(Comparator.comparing(WorkspaceState::workspaceId));
        return new Snapshot(rootIds, List.copyOf(states));
    }

    private String nullable(Long value) {
        return value == null ? "~" : value.toString();
    }

    private Long parseNullable(String value) {
        return "~".equals(value) ? null : parsePositive(value, "snapshot ID");
    }

    private Long parsePositive(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new WorkspaceValidationException("Workspace migration snapshot " + field + " is invalid");
        }
    }

    private String digest(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record WorkspaceState(
            Long workspaceId,
            Long teamId,
            Long companyId,
            Long parentId,
            Long rootId,
            String path) {
    }

    private record Snapshot(List<Long> rootIds, List<WorkspaceState> states) {
        private static Snapshot from(List<Long> rootIds, List<WorkspaceEntity> workspaces) {
            return new Snapshot(rootIds, workspaces.stream()
                    .map(workspace -> new WorkspaceState(
                            workspace.getWorkspaceId(),
                            workspace.getTeamId(),
                            workspace.getCompanyId(),
                            workspace.getParentId(),
                            workspace.getRootId(),
                            workspace.getPath()))
                    .sorted(Comparator.comparing(WorkspaceState::workspaceId))
                    .toList());
        }

        private List<Long> workspaceIds() {
            return states.stream().map(WorkspaceState::workspaceId).toList();
        }

        private WorkspaceState state(Long workspaceId) {
            return states.stream()
                    .filter(state -> state.workspaceId().equals(workspaceId))
                    .findFirst()
                    .orElseThrow(() -> new WorkspaceValidationException(
                            "Workspace migration snapshot does not contain ID: " + workspaceId));
        }
    }
}
