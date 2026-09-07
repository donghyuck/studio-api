package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.rag.RagObjectScope;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeFingerprint;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeMigrationSnapshot;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeMigrationVerification;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeMigrationVerifier;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamMigrationKnowledgePort;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;

/**
 * Restart-safe Team migration knowledge adapter.
 * <p>
 * The complete immutable snapshot is stored in the migration run's TEXT/LONGTEXT reference as
 * versioned canonical JSON, URL-safe Base64, and SHA-256. No source row, file, vector, or index job
 * is copied or mutated. The generated Team manifest is deterministic, so rollback is a verified
 * no-op after validating the snapshot reference.
 */
public final class PortableTeamMigrationKnowledgeAdapter implements TeamMigrationKnowledgePort {

    private static final int SNAPSHOT_VERSION = 1;
    private static final String REFERENCE_PREFIX = "tksnap.v1.";
    private static final int MAX_REFERENCE_CHARS = 1_000_000;
    private static final int MAX_JSON_BYTES = 700_000;
    private static final long SNAPSHOT_TEAM_ID = Long.MAX_VALUE;

    private final PrincipalResolver principalResolver;
    private final WorkspaceTreeService workspaceTreeService;
    private final TeamAuthorizationPort teamAuthorization;
    private final List<TeamKnowledgeSourceContributor> contributors;
    private final RagPipelineService ragPipelineService;
    private final TeamKnowledgeMigrationVerifier migrationVerifier;
    private final ObjectMapper objectMapper;
    private final int maxWorkspaces;
    private final int maxSources;

    public PortableTeamMigrationKnowledgeAdapter(
            PrincipalResolver principalResolver,
            WorkspaceTreeService workspaceTreeService,
            TeamAuthorizationPort teamAuthorization,
            List<TeamKnowledgeSourceContributor> contributors,
            RagPipelineService ragPipelineService,
            TeamKnowledgeMigrationVerifier migrationVerifier,
            ObjectMapper objectMapper,
            int maxWorkspaces,
            int maxSources) {
        if (principalResolver == null || workspaceTreeService == null || teamAuthorization == null
                || ragPipelineService == null || migrationVerifier == null || objectMapper == null) {
            throw new IllegalArgumentException("migration knowledge dependencies are required");
        }
        if (maxWorkspaces <= 0 || maxSources <= 0) {
            throw new IllegalArgumentException("migration knowledge limits must be positive");
        }
        this.principalResolver = principalResolver;
        this.workspaceTreeService = workspaceTreeService;
        this.teamAuthorization = teamAuthorization;
        this.contributors = contributors == null ? List.of() : List.copyOf(contributors);
        this.ragPipelineService = ragPipelineService;
        this.migrationVerifier = migrationVerifier;
        this.objectMapper = objectMapper;
        this.maxWorkspaces = maxWorkspaces;
        this.maxSources = maxSources;
    }

    @Override
    public KnowledgeSnapshot captureBeforeSnapshot(List<Long> sourceRootWorkspaceIds) {
        List<Long> roots = normalizeRoots(sourceRootWorkspaceIds);
        List<Long> workspaceIds = workspaceIds(roots);
        List<TeamKnowledgeSourceRef> sources = contribute(SNAPSHOT_TEAM_ID, workspaceIds);
        Map<RagObjectScope, Long> vectorCounts = vectorCounts(sources);
        String checksum = TeamKnowledgeFingerprint.create(SNAPSHOT_TEAM_ID, null, sources);
        SnapshotState state = new SnapshotState(roots, workspaceIds, sources, vectorCounts, checksum);
        String reference = encode(state);
        return new KnowledgeSnapshot(
                reference,
                count(sources, TeamKnowledgeSourceType.ATTACHMENT),
                count(sources, TeamKnowledgeSourceType.WIKI),
                count(sources, TeamKnowledgeSourceType.WEB_SOURCE),
                vectorCounts.values().stream().mapToLong(Long::longValue).sum(),
                checksum);
    }

    @Override
    public void attachExistingKnowledge(
            String runId,
            Long targetTeamId,
            List<Long> sourceRootWorkspaceIds,
            String snapshotReference) {
        required(runId, "runId");
        Long teamId = positive(targetTeamId, "targetTeamId");
        SnapshotState snapshot = snapshot(snapshotReference, sourceRootWorkspaceIds);
        List<TeamKnowledgeSourceRef> current = contribute(teamId, snapshot.workspaceIds());
        List<TeamKnowledgeSourceRef> expected = rebind(snapshot.sources(), teamId);
        if (!canonical(current).equals(canonical(expected))) {
            throw new IllegalStateException("Knowledge sources changed after dry-run");
        }
        TeamKnowledgeMigrationVerification verification = migrationVerifier.verify(
                new TeamKnowledgeMigrationSnapshot(
                        snapshotReference, teamId, expected, snapshot.vectorCounts()),
                manifest(teamId, current));
        if (!verification.valid()) {
            throw new IllegalStateException("Existing vector state changed after dry-run: "
                    + verification.vectorCountMismatches());
        }
    }

    @Override
    public MigrationVerification verifyExistingKnowledge(
            String runId,
            Long targetTeamId,
            List<Long> sourceRootWorkspaceIds,
            String snapshotReference) {
        required(runId, "runId");
        Long teamId = positive(targetTeamId, "targetTeamId");
        SnapshotState snapshot = snapshot(snapshotReference, sourceRootWorkspaceIds);
        List<TeamKnowledgeSourceRef> expected = rebind(snapshot.sources(), teamId);
        List<TeamKnowledgeSourceRef> current = contribute(teamId, snapshot.workspaceIds());
        TeamKnowledgeManifest expectedManifest = manifest(teamId, expected);
        TeamKnowledgeManifest currentManifest = manifest(teamId, current);
        TeamKnowledgeMigrationVerification verification = migrationVerifier.verify(
                new TeamKnowledgeMigrationSnapshot(
                        snapshotReference, teamId, expected, snapshot.vectorCounts()),
                currentManifest);
        boolean manifestMatches = expectedManifest.corpusFingerprint()
                .equals(currentManifest.corpusFingerprint());
        boolean matches = verification.valid() && manifestMatches;
        return new MigrationVerification(matches, String.join("; ",
                "snapshotVersion=v" + SNAPSHOT_VERSION,
                "sourceFingerprint=" + (manifestMatches ? "MATCH" : "MISMATCH"),
                "missing=" + verification.missingSources().size(),
                "unexpected=" + verification.unexpectedSources().size(),
                "vectorMismatches=" + verification.vectorCountMismatches().size(),
                "restartSafe=true"));
    }

    @Override
    public void rollbackGeneratedState(String runId, Long targetTeamId, String snapshotReference) {
        required(runId, "runId");
        positive(targetTeamId, "targetTeamId");
        decode(required(snapshotReference, "snapshotReference"));
        // Deterministic manifest state is reconstructed from the immutable snapshot; no row was created.
    }

    private TeamKnowledgeManifest manifest(Long teamId, List<TeamKnowledgeSourceRef> sources) {
        String fingerprint = TeamKnowledgeFingerprint.create(teamId, null, sources);
        return new TeamKnowledgeManifest(
                teamId,
                null,
                "tcorpus-" + fingerprint.substring(0, 24),
                fingerprint,
                Long.toString(teamAuthorization.permissionVersion(teamId)),
                sources);
    }

    private List<Long> workspaceIds(List<Long> roots) {
        ApplicationPrincipal principal = principalResolver.currentOrNull();
        if (principal == null || principal.getUserId() == null || principal.getUserId() <= 0) {
            throw new IllegalStateException("Authenticated migration actor is required");
        }
        boolean platformAdmin = principal.roles().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role));
        WorkspaceAccessContext actor = new WorkspaceAccessContext(
                principal.getUserId(), principal.getUsername(), platformAdmin);
        LinkedHashSet<Long> workspaceIds = new LinkedHashSet<>();
        for (Long rootId : roots) {
            workspaceIds.add(workspaceTreeService.getById(rootId, actor).id());
            workspaceTreeService.getDescendants(rootId, actor).stream()
                    .map(studio.one.platform.workspace.domain.model.WorkspaceRef::id)
                    .forEach(workspaceIds::add);
            if (workspaceIds.size() > maxWorkspaces) {
                throw new IllegalArgumentException("Migration Workspace scope exceeds configured limit");
            }
        }
        return List.copyOf(workspaceIds);
    }

    private List<TeamKnowledgeSourceRef> contribute(Long teamId, List<Long> workspaceIds) {
        List<TeamKnowledgeSourceRef> result = new ArrayList<>();
        for (TeamKnowledgeSourceContributor contributor : contributors) {
            result.addAll(contributor.contribute(new TeamKnowledgeContributionRequest(
                    teamId, new LinkedHashSet<>(workspaceIds), maxSources)));
            if (result.size() > maxSources) {
                throw new IllegalArgumentException("Migration knowledge source count exceeds configured limit");
            }
        }
        return result.stream().distinct()
                .sorted(Comparator.comparing(TeamKnowledgeSourceRef::canonicalValue))
                .toList();
    }

    private Map<RagObjectScope, Long> vectorCounts(List<TeamKnowledgeSourceRef> sources) {
        Map<RagObjectScope, Long> result = new LinkedHashMap<>();
        sources.stream().map(TeamKnowledgeSourceRef::objectScope).distinct()
                .sorted(Comparator.comparing(RagObjectScope::canonicalValue))
                .forEach(scope -> result.put(scope,
                        ragPipelineService.countByObject(scope.objectType(), scope.objectId())));
        return Map.copyOf(result);
    }

    private List<TeamKnowledgeSourceRef> rebind(List<TeamKnowledgeSourceRef> sources, Long targetTeamId) {
        return sources.stream()
                .map(source -> new TeamKnowledgeSourceRef(
                        targetTeamId,
                        source.workspaceId(),
                        source.sourceType(),
                        source.objectType(),
                        source.objectId(),
                        source.revisionId(),
                        source.partitionIds()))
                .toList();
    }

    private Set<String> canonical(List<TeamKnowledgeSourceRef> sources) {
        return sources.stream()
                .map(source -> String.join("|",
                        source.workspaceId().toString(),
                        source.sourceType().name(),
                        source.objectScope().canonicalValue(),
                        source.revisionId() == null ? "" : source.revisionId()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String encode(SnapshotState state) {
        SnapshotPayload payload = new SnapshotPayload(
                SNAPSHOT_VERSION,
                state.roots(),
                state.workspaceIds(),
                state.sources().stream().map(SourcePayload::from).toList(),
                state.vectorCounts().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(
                                Comparator.comparing(RagObjectScope::canonicalValue)))
                        .map(VectorCountPayload::from)
                        .toList(),
                state.checksum());
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            if (json.length > MAX_JSON_BYTES) {
                throw new IllegalArgumentException("Knowledge snapshot JSON exceeds bounded size");
            }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
            String reference = REFERENCE_PREFIX + encoded + "." + sha256(json);
            if (reference.length() > MAX_REFERENCE_CHARS) {
                throw new IllegalArgumentException("Knowledge snapshot reference exceeds bounded size");
            }
            return reference;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Knowledge snapshot serialization failed", ex);
        }
    }

    private SnapshotState snapshot(String reference, List<Long> roots) {
        SnapshotState state = decode(required(reference, "snapshotReference"));
        if (!state.roots().equals(normalizeRoots(roots))) {
            throw new IllegalArgumentException("Knowledge snapshot roots do not match migration request");
        }
        return state;
    }

    private SnapshotState decode(String reference) {
        if (reference.length() > MAX_REFERENCE_CHARS || !reference.startsWith(REFERENCE_PREFIX)) {
            throw new IllegalArgumentException("Unsupported or oversized knowledge snapshot reference");
        }
        String material = reference.substring(REFERENCE_PREFIX.length());
        int separator = material.lastIndexOf('.');
        if (separator <= 0 || separator == material.length() - 1) {
            throw new IllegalArgumentException("Malformed knowledge snapshot reference");
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(material.substring(0, separator));
            if (json.length > MAX_JSON_BYTES) {
                throw new IllegalArgumentException("Knowledge snapshot JSON exceeds bounded size");
            }
            String expectedHash = material.substring(separator + 1);
            if (!MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.US_ASCII),
                    sha256(json).getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Knowledge snapshot checksum mismatch");
            }
            SnapshotPayload payload = objectMapper.readValue(json, SnapshotPayload.class);
            if (payload.version() != SNAPSHOT_VERSION) {
                throw new IllegalArgumentException("Unsupported knowledge snapshot version");
            }
            List<Long> roots = normalizeRoots(payload.roots());
            List<Long> workspaceIds = payload.workspaceIds() == null
                    ? List.of()
                    : payload.workspaceIds().stream().map(id -> positive(id, "workspaceId"))
                            .distinct().sorted().toList();
            if (workspaceIds.isEmpty() || workspaceIds.size() > maxWorkspaces) {
                throw new IllegalArgumentException("Invalid knowledge snapshot Workspace scope");
            }
            List<TeamKnowledgeSourceRef> sources = payload.sources() == null
                    ? List.of()
                    : payload.sources().stream().map(SourcePayload::toSource).toList();
            if (sources.size() > maxSources
                    || sources.stream().anyMatch(source -> source.teamId() != SNAPSHOT_TEAM_ID
                            || !workspaceIds.contains(source.workspaceId()))) {
                throw new IllegalArgumentException("Invalid knowledge snapshot source scope");
            }
            Map<RagObjectScope, Long> vectorCounts = new LinkedHashMap<>();
            if (payload.vectorCounts() != null) {
                payload.vectorCounts().forEach(value -> {
                    RagObjectScope scope = value.toScope();
                    if (value.count() < 0 || vectorCounts.put(scope, value.count()) != null) {
                        throw new IllegalArgumentException("Invalid knowledge snapshot vector counts");
                    }
                });
            }
            if (!sources.stream().map(TeamKnowledgeSourceRef::objectScope).toList()
                    .containsAll(vectorCounts.keySet())) {
                throw new IllegalArgumentException("Vector count scope is absent from snapshot sources");
            }
            String calculatedChecksum = TeamKnowledgeFingerprint.create(SNAPSHOT_TEAM_ID, null, sources);
            if (!calculatedChecksum.equals(payload.checksum())) {
                throw new IllegalArgumentException("Knowledge source checksum mismatch");
            }
            return new SnapshotState(
                    roots, workspaceIds, List.copyOf(sources), Map.copyOf(vectorCounts), calculatedChecksum);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Knowledge snapshot decoding failed", ex);
        }
    }

    private List<Long> normalizeRoots(List<Long> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("sourceRootWorkspaceIds must not be empty");
        }
        return roots.stream().map(root -> positive(root, "sourceRootWorkspaceId"))
                .distinct().sorted().toList();
    }

    private long count(List<TeamKnowledgeSourceRef> sources, TeamKnowledgeSourceType type) {
        return sources.stream().filter(source -> source.sourceType() == type).count();
    }

    private static Long positive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record SnapshotState(
            List<Long> roots,
            List<Long> workspaceIds,
            List<TeamKnowledgeSourceRef> sources,
            Map<RagObjectScope, Long> vectorCounts,
            String checksum) {
    }

    private record SnapshotPayload(
            int version,
            List<Long> roots,
            List<Long> workspaceIds,
            List<SourcePayload> sources,
            List<VectorCountPayload> vectorCounts,
            String checksum) {
    }

    private record SourcePayload(
            Long teamId,
            Long workspaceId,
            String sourceType,
            String objectType,
            String objectId,
            String revisionId,
            List<String> partitionIds) {

        static SourcePayload from(TeamKnowledgeSourceRef source) {
            return new SourcePayload(
                    source.teamId(), source.workspaceId(), source.sourceType().name(),
                    source.objectType(), source.objectId(), source.revisionId(),
                    source.partitionIds().stream().sorted().toList());
        }

        TeamKnowledgeSourceRef toSource() {
            return new TeamKnowledgeSourceRef(
                    teamId,
                    workspaceId,
                    TeamKnowledgeSourceType.valueOf(sourceType),
                    objectType,
                    objectId,
                    revisionId,
                    partitionIds == null ? Set.of() : Set.copyOf(partitionIds));
        }
    }

    private record VectorCountPayload(
            String objectType,
            String objectId,
            List<String> partitionIds,
            long count) {

        static VectorCountPayload from(Map.Entry<RagObjectScope, Long> entry) {
            return new VectorCountPayload(
                    entry.getKey().objectType(),
                    entry.getKey().objectId(),
                    entry.getKey().partitionIds().stream().sorted().toList(),
                    entry.getValue());
        }

        RagObjectScope toScope() {
            return new RagObjectScope(
                    objectType,
                    objectId,
                    partitionIds == null ? Set.of() : Set.copyOf(partitionIds));
        }
    }
}
