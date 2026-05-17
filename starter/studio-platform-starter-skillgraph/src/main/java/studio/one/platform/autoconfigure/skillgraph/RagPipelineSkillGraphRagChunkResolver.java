package studio.one.platform.autoconfigure.skillgraph;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;

@RequiredArgsConstructor
class RagPipelineSkillGraphRagChunkResolver implements SkillGraphRagChunkResolver {

    private final RagPipelineService ragPipelineService;

    @Override
    public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int limit) {
        return ragPipelineService.listByObject(objectType, objectId, limit).stream()
                .map(this::toChunk)
                .toList();
    }

    private ResolvedRagChunk toChunk(RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String documentId = text(firstPresent(metadata, VectorRecord.KEY_DOCUMENT_ID, "documentId", "sourceDocumentId"));
        documentId = documentId == null ? result.documentId() : documentId;
        String chunkId = text(firstPresent(metadata, VectorRecord.KEY_CHUNK_ID, "chunkId"));
        chunkId = chunkId == null ? documentId : chunkId;
        return new ResolvedRagChunk(chunkId, documentId, result.content());
    }

    private Object firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
