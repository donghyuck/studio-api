package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.RagObjectScope;
import studio.one.platform.ai.core.rag.team.TeamCitationAuthorizer;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.ai.service.pipeline.RagPipelineService;

class TeamRagRetrievalServiceTest {

    @Test
    void reusesAuthorizedObjectScopesAndReturnsCacheIsolationValues() {
        RagPipelineService pipeline = mock(RagPipelineService.class);
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        TeamCitationAuthorizer citationAuthorizer = mock(TeamCitationAuthorizer.class);
        TeamKnowledgeManifest manifest = manifest();
        when(resolver.resolveAuthorized(7L, null)).thenReturn(Optional.of(manifest));
        RagSearchResult result = new RagSearchResult(
                "document-1",
                "content",
                Map.of("objectType", "attachment", "objectId", "10", "revisionId", "rev-1"),
                0.9d);
        when(pipeline.searchByObjects(any(), eq(manifest.objectScopes()), eq(8)))
                .thenReturn(List.of(result));
        when(citationAuthorizer.canRead(7L, 2L, "attachment", "10", "rev-1"))
                .thenReturn(true);
        TeamRagRetrievalService service = new TeamRagRetrievalService(pipeline, resolver, 8);

        var retrieval = service.retrieve(7L, null, new RagSearchRequest("question", 3));

        assertThat(retrieval).isPresent();
        assertThat(retrieval.orElseThrow().results()).containsExactly(result);
        assertThat(retrieval.orElseThrow().cacheScope().permissionVersion()).isEqualTo("permission-1");
    }

    @Test
    void hidesMissingOrDeniedTeamWithoutSearching() {
        RagPipelineService pipeline = mock(RagPipelineService.class);
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        TeamCitationAuthorizer citationAuthorizer = mock(TeamCitationAuthorizer.class);
        when(resolver.resolveAuthorized(7L, 2L)).thenReturn(Optional.empty());
        TeamRagRetrievalService service = new TeamRagRetrievalService(pipeline, resolver, 8);

        assertThat(service.retrieve(7L, 2L, new RagSearchRequest("question", 3))).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(pipeline, citationAuthorizer);
    }

    @Test
    void removesResultWhenLiveCitationAuthorizationChanged() {
        RagPipelineService pipeline = mock(RagPipelineService.class);
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        TeamCitationAuthorizer citationAuthorizer = mock(TeamCitationAuthorizer.class);
        TeamKnowledgeManifest manifest = manifest();
        when(resolver.resolveAuthorized(7L, null)).thenReturn(Optional.of(manifest));
        when(pipeline.searchByObjects(any(), eq(manifest.objectScopes()), eq(8)))
                .thenReturn(List.of(new RagSearchResult(
                        "document-1", "content",
                        Map.of("objectType", "attachment", "objectId", "10"), 0.9d)));
        TeamRagRetrievalService service = new TeamRagRetrievalService(pipeline, resolver, 8);

        var retrieval = service.retrieve(7L, null, new RagSearchRequest("question", 3));

        assertThat(retrieval.orElseThrow().results()).hasSize(1);
    }

    @Test
    void routesFollowUpQueriesToPrimaryCandidateScopesAndReranksDuplicates() {
        RagPipelineService pipeline = mock(RagPipelineService.class);
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        TeamKnowledgeManifest manifest = TeamKnowledgeManifest.create(
                7L,
                null,
                "corpus-2",
                "permission-2",
                List.of(
                        new TeamKnowledgeSourceRef(
                                7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                                "attachment", "10", "rev-1", Set.of()),
                        new TeamKnowledgeSourceRef(
                                7L, 3L, TeamKnowledgeSourceType.ATTACHMENT,
                                "attachment", "11", "rev-2", Set.of())));
        when(resolver.resolveAuthorized(7L, null)).thenReturn(Optional.of(manifest));
        RagSearchRequest primary = new RagSearchRequest("현재 질문과 대화 맥락", 4);
        RagSearchRequest expanded = new RagSearchRequest("행동 사건 갈등 변화", 4);
        RagSearchResult primaryHit = result("doc-10-a", "10", "chunk-a", 0.82d);
        RagSearchResult repeatedHit = result("doc-10-a", "10", "chunk-a", 0.79d);
        RagSearchResult secondaryHit = result("doc-10-b", "10", "chunk-b", 0.91d);
        when(pipeline.searchByObjects(primary, manifest.objectScopes(), 8))
                .thenReturn(List.of(primaryHit));
        when(pipeline.searchByObjects(expanded, List.of(new RagObjectScope("attachment", "10")), 8))
                .thenReturn(List.of(secondaryHit, repeatedHit));
        TeamRagRetrievalService service = new TeamRagRetrievalService(pipeline, resolver, 8);

        var retrieval = service.retrieveQueries(7L, null, List.of(primary, expanded)).orElseThrow();

        assertThat(retrieval.results()).extracting(RagSearchResult::documentId)
                .containsExactly("doc-10-a", "doc-10-b");
        assertThat(retrieval.executedQueryCount()).isEqualTo(2);
        assertThat(retrieval.sourceScopeCount()).isEqualTo(2);
        assertThat(retrieval.routedScopeCount()).isEqualTo(1);
        assertThat(retrieval.routingApplied()).isTrue();
        assertThat(retrieval.coverageFallback()).isFalse();
        verify(pipeline).searchByObjects(expanded, List.of(new RagObjectScope("attachment", "10")), 8);
    }

    private RagSearchResult result(String documentId, String objectId, String chunkId, double score) {
        return new RagSearchResult(
                documentId,
                documentId,
                Map.of(
                        "objectType", "attachment",
                        "objectId", objectId,
                        "chunkId", chunkId,
                        "revisionId", "rev-1"),
                score);
    }

    private TeamKnowledgeManifest manifest() {
        return TeamKnowledgeManifest.create(
                7L,
                null,
                "corpus-1",
                "permission-1",
                List.of(new TeamKnowledgeSourceRef(
                        7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                        "attachment", "10", "rev-1", Set.of())));
    }
}
