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

    @Override
    public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int offset, int limit) {
        return ragPipelineService.listByObject(objectType, objectId, offset, limit).stream()
                .map(this::toChunk)
                .toList();
    }

    private ResolvedRagChunk toChunk(RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String documentId = text(firstPresent(metadata, VectorRecord.KEY_DOCUMENT_ID, "documentId", "sourceDocumentId"));
        documentId = documentId == null ? result.documentId() : documentId;
        String chunkId = text(firstPresent(metadata, VectorRecord.KEY_CHUNK_ID, "chunkId"));
        chunkId = chunkId == null ? documentId : chunkId;
        Integer tokenCount = integer(firstPresent(metadata, VectorRecord.KEY_CHUNK_TOKEN_COUNT, "tokenCount"));
        String warningStatus = firstPresent(metadata, VectorRecord.KEY_TOKENIZER_WARNINGS, "warnings") == null
                ? null
                : "WARNING";
        return new ResolvedRagChunk(
                chunkId,
                documentId,
                result.content(),
                integer(firstPresent(metadata, VectorRecord.KEY_CHUNK_INDEX, "chunkOrder")),
                integer(firstPresent(metadata, VectorRecord.KEY_PAGE, "page")),
                text(firstPresent(metadata, VectorRecord.KEY_HEADING_PATH, "headingPath", "section")),
                tokenCount,
                warningStatus);
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

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
