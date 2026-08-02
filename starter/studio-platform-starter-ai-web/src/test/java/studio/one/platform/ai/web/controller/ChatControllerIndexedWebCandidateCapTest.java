package studio.one.platform.ai.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.indexed.ResolvedIndexedRagSource;

class ChatControllerIndexedWebCandidateCapTest {

    @Test
    void limitsEachSitePageWithoutChangingGlobalScoreOrder() {
        List<RagSearchResult> candidates = List.of(
                result("a-1", "page-a", 0.99),
                result("a-2", "page-a", 0.98),
                result("a-3", "page-a", 0.97),
                result("b-1", "page-b", 0.96),
                result("b-2", "page-b", 0.95),
                result("c-1", "page-c", 0.94));

        List<RagSearchResult> result =
                ChatController.capIndexedWebCandidatesPerPage(candidates, 2, 5);

        assertEquals(List.of("a-1", "a-2", "b-1", "b-2", "c-1"), result.stream()
                .map(RagSearchResult::documentId)
                .toList());
    }

    @Test
    void treatsUnpartitionedCandidatesIndependentlyForLegacyCompatibility() {
        List<RagSearchResult> candidates = List.of(
                new RagSearchResult("legacy-1", "one", Map.of(), 0.9),
                new RagSearchResult("legacy-2", "two", Map.of(), 0.8),
                new RagSearchResult("legacy-3", "three", Map.of(), 0.7));

        List<RagSearchResult> result =
                ChatController.capIndexedWebCandidatesPerPage(candidates, 1, 3);

        assertEquals(3, result.size());
    }

    @Test
    void acceptsSitePageRevisionThatBelongsToSelectedCorpus() {
        ResolvedIndexedRagSource source = siteSource(Set.of("page-revision-1", "page-revision-2"));

        assertTrue(ChatController.belongsToIndexedSource(
                result("chunk-1", "page-revision-1", 0.9), source));
        assertFalse(ChatController.belongsToIndexedSource(
                result("chunk-2", "different-page-revision", 0.8), source));
    }

    @Test
    void keepsSinglePageRevisionPinning() {
        ResolvedIndexedRagSource source = siteSource(Set.of());

        assertTrue(ChatController.belongsToIndexedSource(
                new RagSearchResult(
                        "chunk-1", "one", Map.of("sourceRevisionId", "corpus-revision"), 0.9),
                source));
        assertFalse(ChatController.belongsToIndexedSource(
                new RagSearchResult(
                        "chunk-2", "two", Map.of("sourceRevisionId", "other-revision"), 0.8),
                source));
    }

    private static RagSearchResult result(String id, String pageRevisionId, double score) {
        return new RagSearchResult(
                id,
                id,
                Map.of("pageRevisionId", pageRevisionId, "partitionId", pageRevisionId),
                score);
    }

    private static ResolvedIndexedRagSource siteSource(Set<String> partitions) {
        return new ResolvedIndexedRagSource(
                "web_source",
                "source-1",
                "corpus-revision",
                "web_source",
                "source-1",
                "content-hash",
                "embedding-default",
                "embedding-space",
                partitions,
                Map.of());
    }
}
