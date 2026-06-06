package studio.one.platform.autoconfigure.skillgraph;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
    public long countByObject(String objectType, String objectId) {
        return ragPipelineService.countByObject(objectType, objectId);
    }

    @Override
    public long countByObject(String objectType, String objectId, String query) {
        return ragPipelineService.countByObject(objectType, objectId, query);
    }

    @Override
    public Page<ResolvedRagChunk> pageByObject(String objectType, String objectId, Pageable pageable) {
        int offset = pageable == null ? 0 : (int) Math.min(Integer.MAX_VALUE, pageable.getOffset());
        int limit = pageable == null || pageable.getPageSize() <= 0 ? 50 : pageable.getPageSize();
        return new PageImpl<>(
                listByObject(objectType, objectId, offset, limit),
                pageable,
                countByObject(objectType, objectId));
    }

    @Override
    public List<ResolvedRagChunk> listByObject(String objectType, String objectId, int offset, int limit) {
        return ragPipelineService.listByObject(objectType, objectId, offset, limit).stream()
                .map(this::toChunk)
                .toList();
    }

    @Override
    public List<ResolvedRagChunk> listByObject(
            String objectType,
            String objectId,
            String query,
            int offset,
            int limit) {
        return ragPipelineService.listByObject(objectType, objectId, query, offset, limit).stream()
                .map(this::toChunk)
                .toList();
    }

    @Override
    public List<ResolvedRagChunk> listByChunkIds(String objectType, Set<String> chunkIds) {
        return ragPipelineService.listByChunkIds(objectType, chunkIds).stream()
                .map(this::toChunk)
                .toList();
    }

    private ResolvedRagChunk toChunk(RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String documentId = text(firstPresent(metadata, VectorRecord.KEY_DOCUMENT_ID, "documentId", "sourceDocumentId"));
        documentId = documentId == null ? result.documentId() : documentId;
        String objectId = text(firstPresent(metadata, VectorRecord.KEY_OBJECT_ID, "objectId"));
        objectId = objectId == null ? result.documentId() : objectId;
        String chunkId = text(firstPresent(metadata, VectorRecord.KEY_CHUNK_ID, "chunkId"));
        chunkId = chunkId == null ? documentId : chunkId;
        Integer tokenCount = integer(firstPresent(metadata, VectorRecord.KEY_CHUNK_TOKEN_COUNT, "tokenCount"));
        String warningStatus = firstPresent(metadata, VectorRecord.KEY_TOKENIZER_WARNINGS, "warnings") == null
                ? null
                : "WARNING";
        return new ResolvedRagChunk(
                chunkId,
                documentId,
                objectId,
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
