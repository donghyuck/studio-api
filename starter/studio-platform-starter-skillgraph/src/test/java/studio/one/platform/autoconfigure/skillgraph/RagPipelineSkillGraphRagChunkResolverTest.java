package studio.one.platform.autoconfigure.skillgraph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;

class RagPipelineSkillGraphRagChunkResolverTest {

    @Test
    void usesVectorDocumentIdWhenMetadataHasNoObjectId() {
        RagPipelineService pipelineService = new FakeRagPipelineService(List.of(new RagSearchResult(
                "900001",
                "Spring Boot skill test",
                Map.of(
                        "documentId", "skill-test-doc-01",
                        "chunkId", "skill-test-doc-01-chunk-000"),
                0.0)));
        RagPipelineSkillGraphRagChunkResolver resolver =
                new RagPipelineSkillGraphRagChunkResolver(pipelineService, JsonMapper.builder().build());

        var chunks = resolver.listByObject("attachment", null, 0, 50);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).objectId()).isEqualTo("900001");
        assertThat(chunks.get(0).documentId()).isEqualTo("skill-test-doc-01");
    }

    private record FakeRagPipelineService(List<RagSearchResult> results) implements RagPipelineService {

        @Override
        public void index(RagIndexRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RagSearchResult> search(RagSearchRequest request) {
            return results;
        }

        @Override
        public List<RagSearchResult> searchByObject(
                RagSearchRequest request,
                String objectType,
                String objectId) {
            return results;
        }

        @Override
        public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
            return results;
        }

        @Override
        public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
            return Optional.empty();
        }
    }
}
