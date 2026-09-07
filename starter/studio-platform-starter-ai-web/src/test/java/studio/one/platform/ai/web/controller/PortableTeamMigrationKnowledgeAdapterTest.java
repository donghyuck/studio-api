package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.json.JsonMapper;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.pipeline.RagTeamKnowledgeMigrationVerifier;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;
import studio.one.platform.workspace.domain.model.WorkspaceAccessMode;
import studio.one.platform.workspace.domain.model.WorkspaceRef;
import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

class PortableTeamMigrationKnowledgeAdapterTest {

    @Test
    void snapshotReferenceSurvivesFreshAdapterApplyVerifyAndNoOpRollback() {
        Fixtures fixtures = fixtures();
        PortableTeamMigrationKnowledgeAdapter captureAdapter = fixtures.adapter();

        var snapshot = captureAdapter.captureBeforeSnapshot(List.of(2L));

        PortableTeamMigrationKnowledgeAdapter restartedAdapter = fixtures.adapter();
        restartedAdapter.attachExistingKnowledge("run-1", 7L, List.of(2L), snapshot.reference());
        var verification = restartedAdapter.verifyExistingKnowledge(
                "run-1", 7L, List.of(2L), snapshot.reference());
        restartedAdapter.rollbackGeneratedState("run-1", 7L, snapshot.reference());
        var afterRollback = restartedAdapter.verifyExistingKnowledge(
                "run-1", 7L, List.of(2L), snapshot.reference());

        assertThat(snapshot.reference()).startsWith("tksnap.v1.");
        assertThat(snapshot.attachmentCount()).isEqualTo(1);
        assertThat(snapshot.vectorCount()).isEqualTo(3);
        assertThat(verification.matches()).isTrue();
        assertThat(verification.details()).contains("restartSafe=true");
        assertThat(afterRollback.matches()).isTrue();
        assertThat(fixtures.pipeline().indexCalls).isZero();

        ArgumentCaptor<WorkspaceAccessContext> actor = ArgumentCaptor.forClass(WorkspaceAccessContext.class);
        org.mockito.Mockito.verify(fixtures.workspaces()).getById(
                org.mockito.ArgumentMatchers.eq(2L), actor.capture());
        assertThat(actor.getValue().platformAdmin()).isTrue();
    }

    @Test
    void rejectsTamperedPortableSnapshot() {
        Fixtures fixtures = fixtures();
        var snapshot = fixtures.adapter().captureBeforeSnapshot(List.of(2L));
        char last = snapshot.reference().charAt(snapshot.reference().length() - 1);
        String tampered = snapshot.reference().substring(0, snapshot.reference().length() - 1)
                + (last == '0' ? "1" : "0");

        assertThatThrownBy(() -> fixtures.adapter().attachExistingKnowledge(
                "run-1", 7L, List.of(2L), tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    private Fixtures fixtures() {
        PrincipalResolver principals = mock(PrincipalResolver.class);
        ApplicationPrincipal principal = mock(ApplicationPrincipal.class);
        when(principal.getUserId()).thenReturn(9L);
        when(principal.getUsername()).thenReturn("admin");
        when(principal.getRoles()).thenReturn(Set.of("ROLE_ADMIN"));
        when(principal.roles()).thenReturn(Set.of("ROLE_ADMIN"));
        when(principals.currentOrNull()).thenReturn(principal);
        WorkspaceTreeService workspaces = mock(WorkspaceTreeService.class);
        when(workspaces.getById(any(), any())).thenReturn(workspace(2L));
        when(workspaces.getDescendants(any(), any())).thenReturn(List.of(workspace(3L)));
        TeamAuthorizationPort authorization = mock(TeamAuthorizationPort.class);
        when(authorization.permissionVersion(7L)).thenReturn(4L);
        TeamKnowledgeSourceContributor contributor = mock(TeamKnowledgeSourceContributor.class);
        when(contributor.contribute(any())).thenAnswer(invocation -> {
            var request = (studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest)
                    invocation.getArgument(0);
            return List.of(new TeamKnowledgeSourceRef(
                    request.teamId(), 2L, TeamKnowledgeSourceType.ATTACHMENT,
                    "attachment", "10", "rev-1", Set.of("partition-1")));
        });
        CountingPipeline pipeline = new CountingPipeline(3);
        return new Fixtures(principals, workspaces, authorization, contributor, pipeline);
    }

    private WorkspaceRef workspace(Long id) {
        return new WorkspaceRef(
                id, null, 7L, null, id, "workspace", "workspace", "workspace", 0,
                WorkspaceVisibility.PRIVATE, WorkspaceAccessMode.INHERIT, false);
    }

    private record Fixtures(
            PrincipalResolver principals,
            WorkspaceTreeService workspaces,
            TeamAuthorizationPort authorization,
            TeamKnowledgeSourceContributor contributor,
            CountingPipeline pipeline) {

        PortableTeamMigrationKnowledgeAdapter adapter() {
            return new PortableTeamMigrationKnowledgeAdapter(
                    principals,
                    workspaces,
                    authorization,
                    List.of(contributor),
                    pipeline,
                    new RagTeamKnowledgeMigrationVerifier(pipeline),
                    JsonMapper.builder().build(),
                    100,
                    32);
        }
    }

    private static final class CountingPipeline implements RagPipelineService {
        private final int count;
        private int indexCalls;

        private CountingPipeline(int count) {
            this.count = count;
        }

        @Override
        public void index(RagIndexRequest request) {
            indexCalls++;
        }

        @Override
        public List<RagSearchResult> search(RagSearchRequest request) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> searchByObject(
                RagSearchRequest request, String objectType, String objectId) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
            return java.util.stream.IntStream.range(0, count)
                    .mapToObj(index -> new RagSearchResult(
                            "document-" + index, "content", Map.of(), 1.0d))
                    .toList();
        }

        @Override
        public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
            return Optional.empty();
        }
    }
}
