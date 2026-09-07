package studio.one.application.webknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.platform.ai.core.rag.indexed.ResolvedIndexedRagSource;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest;

class WebKnowledgeTeamKnowledgeSourceContributorTest {

    @Test
    void contributesResolvedCorpusPartitionsWithoutReindexing() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        WebKnowledgeIndexedRagSourceProvider indexed = mock(WebKnowledgeIndexedRagSourceProvider.class);
        WebKnowledgeSourceEntity source = new WebKnowledgeSourceEntity(
                "source-1", 2L, "https://example.com", "https://example.com", "hash", "example.com",
                "Example", "embedding-1", "space-1", "user", Instant.parse("2026-08-31T00:00:00Z"));
        source.completeCorpus("corpus-1", "https://example.com", "space-1", Instant.now());
        when(sources.findByWorkspaceIdAndArchivedFalseOrderByUpdatedAtDesc(2L)).thenReturn(List.of(source));
        when(indexed.resolve("source-1", "corpus-1")).thenReturn(Optional.of(new ResolvedIndexedRagSource(
                "web_source", "source-1", "corpus-1", "web_source", "source-1", "content-hash",
                "embedding-1", "space-1", Set.of("page-rev-1"), Map.of())));

        var result = new WebKnowledgeTeamKnowledgeSourceContributor(sources, indexed).contribute(
                new TeamKnowledgeContributionRequest(7L, Set.of(2L), 10));

        assertThat(result).singleElement().satisfies(ref -> {
            assertThat(ref.objectType()).isEqualTo("web_source");
            assertThat(ref.objectId()).isEqualTo("source-1");
            assertThat(ref.partitionIds()).containsExactly("page-rev-1");
        });
    }
}
