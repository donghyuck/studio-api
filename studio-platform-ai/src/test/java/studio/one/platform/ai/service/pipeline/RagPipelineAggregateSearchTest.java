package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagObjectScope;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;

class RagPipelineAggregateSearchTest {

    @Test
    void searchesExistingObjectScopesAndRanksGlobally() {
        FakePipeline pipeline = new FakePipeline();
        pipeline.results.put("attachment:10", List.of(result("a", "attachment", "10", 0.5)));
        pipeline.results.put("web_source:s1", List.of(result("w", "web_source", "s1", 0.9)));

        List<RagSearchResult> results = pipeline.searchByObjects(
                new RagSearchRequest("question", 2),
                List.of(
                        new RagObjectScope("attachment", "10"),
                        new RagObjectScope("web_source", "s1")),
                4);

        assertThat(results).extracting(RagSearchResult::documentId).containsExactly("w", "a");
        assertThat(pipeline.searched).containsExactly("attachment:10", "web_source:s1");
    }

    @Test
    void failsBeforeSearchingWhenScopeLimitIsExceeded() {
        FakePipeline pipeline = new FakePipeline();

        assertThatThrownBy(() -> pipeline.searchByObjects(
                new RagSearchRequest("question", 2),
                List.of(
                        new RagObjectScope("attachment", "10"),
                        new RagObjectScope("attachment", "11")),
                1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maxScopes");
        assertThat(pipeline.searched).isEmpty();
    }

    private RagSearchResult result(
            String documentId,
            String objectType,
            String objectId,
            double score) {
        return new RagSearchResult(
                documentId,
                documentId,
                Map.of("objectType", objectType, "objectId", objectId, "chunkId", documentId),
                score);
    }

    private static final class FakePipeline implements RagPipelineService {
        private final Map<String, List<RagSearchResult>> results = new java.util.HashMap<>();
        private final List<String> searched = new ArrayList<>();

        @Override
        public void index(RagIndexRequest request) {
        }

        @Override
        public List<RagSearchResult> search(RagSearchRequest request) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> searchByObject(
                RagSearchRequest request, String objectType, String objectId) {
            String key = objectType + ":" + objectId;
            searched.add(key);
            return results.getOrDefault(key, List.of());
        }

        @Override
        public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
            return results.getOrDefault(objectType + ":" + objectId, List.of());
        }

        @Override
        public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
            return Optional.empty();
        }
    }
}
