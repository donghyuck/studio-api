package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;

class TeamKnowledgeSourceControllerTest {

    @Test
    void remainsProxyableForMethodSecurity() {
        assertThat(java.lang.reflect.Modifier.isFinal(
                TeamKnowledgeSourceController.class.getModifiers())).isFalse();
    }

    @Test
    void returnsAuthorizedManifestSourcesForFrontend() {
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        when(resolver.resolveAuthorized(7L, 2L)).thenReturn(Optional.of(TeamKnowledgeManifest.create(
                7L, 2L, "corpus-1", "permission-1",
                List.of(new TeamKnowledgeSourceRef(
                        7L, 2L, TeamKnowledgeSourceType.WEB_SOURCE,
                        "web_source", "source-1", "revision-1", Set.of("page-1"))))));

        var response = new TeamKnowledgeSourceController(resolver).list(7L, 2L);

        assertThat(response.getBody().getData()).singleElement().satisfies(source -> {
            assertThat(source.teamId()).isEqualTo(7L);
            assertThat(source.workspaceId()).isEqualTo(2L);
            assertThat(source.sourceType()).isEqualTo("WEB_SOURCE");
            assertThat(source.sourceId()).isEqualTo("source-1");
            assertThat(source.revisionId()).isEqualTo("revision-1");
            assertThat(source.partitionIds()).containsExactly("page-1");
        });
    }
}
