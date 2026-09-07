package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagObjectScope;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeMigrationSnapshot;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;

class RagTeamKnowledgeMigrationVerifierTest {

    @Test
    void verifiesSourceIdentityAndVectorCountWithoutReindexing() {
        RagObjectScope scope = new RagObjectScope("attachment", "10");
        TeamKnowledgeSourceRef source = new TeamKnowledgeSourceRef(
                7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                scope.objectType(), scope.objectId(), "rev-1", Set.of());
        CountingPipeline pipeline = new CountingPipeline(3);
        var verifier = new RagTeamKnowledgeMigrationVerifier(pipeline);
        var snapshot = new TeamKnowledgeMigrationSnapshot(
                "snapshot-1", 7L, List.of(source), Map.of(scope, 3L));
        var manifest = TeamKnowledgeManifest.create(
                7L, null, "corpus-1", "permission-1", List.of(source));

        var result = verifier.verify(snapshot, manifest);

        assertThat(result.valid()).isTrue();
        assertThat(pipeline.indexCalls).isZero();
    }

    @Test
    void reportsVectorCountMismatchAsCutoverFailure() {
        RagObjectScope scope = new RagObjectScope("attachment", "10");
        TeamKnowledgeSourceRef source = new TeamKnowledgeSourceRef(
                7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                scope.objectType(), scope.objectId(), "rev-1", Set.of());
        var result = new RagTeamKnowledgeMigrationVerifier(new CountingPipeline(2)).verify(
                new TeamKnowledgeMigrationSnapshot(
                        "snapshot-1", 7L, List.of(source), Map.of(scope, 3L)),
                TeamKnowledgeManifest.create(
                        7L, null, "corpus-1", "permission-1", List.of(source)));

        assertThat(result.valid()).isFalse();
        assertThat(result.vectorCountMismatches()).singleElement()
                .asString().contains("expected=3,actual=2");
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
                            "d" + index, "content", Map.of(), 1.0d)).toList();
        }

        @Override
        public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
            return Optional.empty();
        }
    }
}
