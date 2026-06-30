package studio.one.platform.ai.service.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class JdbcRagChunkStageStore implements RagChunkStageStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int INSERT_BATCH_SIZE = 200;

    private final NamedParameterJdbcTemplate template;
    private final ObjectMapper objectMapper;

    public JdbcRagChunkStageStore(NamedParameterJdbcTemplate template, ObjectMapper objectMapper) {
        this.template = Objects.requireNonNull(template, "template");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public void replace(String objectType, String objectId, String documentId, List<RagChunkStage> chunks) {
        deleteByObject(objectType, objectId, documentId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (int offset = 0; offset < chunks.size(); offset += INSERT_BATCH_SIZE) {
            List<RagChunkStage> slice = chunks.subList(offset, Math.min(offset + INSERT_BATCH_SIZE, chunks.size()));
            MapSqlParameterSource[] batch = new MapSqlParameterSource[slice.size()];
            for (int index = 0; index < slice.size(); index++) {
                batch[index] = params(objectType, objectId, documentId, slice.get(index));
            }
            template.batchUpdate("""
                    INSERT INTO tb_ai_rag_chunk_stage(
                        object_type, object_id, document_id, chunk_index, chunk_id, text, metadata, created_at)
                    VALUES (
                        :objectType, :objectId, :documentId, :chunkIndex, :chunkId, :text, :metadata, :createdAt)
                    """, batch);
        }
    }

    @Override
    public List<RagChunkStage> findByObject(String objectType, String objectId, String documentId) {
        return template.query("""
                SELECT object_type, object_id, document_id, chunk_index, chunk_id, text, metadata, created_at
                  FROM tb_ai_rag_chunk_stage
                 WHERE object_type = :objectType
                   AND object_id = :objectId
                   AND ((:documentId IS NULL AND document_id IS NULL) OR document_id = :documentId)
                 ORDER BY chunk_index
                """, scopeParams(objectType, objectId, documentId), new StageRowMapper());
    }

    @Override
    public List<RagChunkStage> findIndexedByObject(String objectType, String objectId, String revisionId) {
        return template.query("""
                SELECT id, object_type, object_id, chunk_index, text, metadata, created_at
                  FROM tb_ai_document_chunk
                 WHERE object_type = :objectType
                   AND object_id = :objectId
                 ORDER BY chunk_index
                """, scopeParams(objectType, objectId, revisionId), new IndexedChunkRowMapper()).stream()
                .filter(stage -> revisionId == null || revisionId.isBlank()
                        || revisionId.equals(text(stage.metadata().get("markdownRevisionId")))
                        || revisionId.equals(text(stage.metadata().get("sourceMarkdownRevisionId")))
                        || revisionId.equals(text(stage.metadata().get("sourceRevisionId")))
                        || revisionId.equals(text(stage.metadata().get("revisionId"))))
                .toList();
    }

    @Override
    public void deleteByObject(String objectType, String objectId, String documentId) {
        template.update("""
                DELETE FROM tb_ai_rag_chunk_stage
                 WHERE object_type = :objectType
                   AND object_id = :objectId
                   AND ((:documentId IS NULL AND document_id IS NULL) OR document_id = :documentId)
                """, scopeParams(objectType, objectId, documentId));
    }

    private MapSqlParameterSource scopeParams(String objectType, String objectId, String documentId) {
        return new MapSqlParameterSource()
                .addValue("objectType", objectType)
                .addValue("objectId", objectId)
                .addValue("documentId", documentId);
    }

    private MapSqlParameterSource params(
            String objectType,
            String objectId,
            String documentId,
            RagChunkStage chunk) {
        return scopeParams(objectType, objectId, documentId)
                .addValue("chunkIndex", chunk.chunkIndex())
                .addValue("chunkId", chunk.chunkId())
                .addValue("text", chunk.text())
                .addValue("metadata", writeJson(chunk.metadata()))
                .addValue("createdAt", Timestamp.from(chunk.createdAt()));
    }

    private String writeJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize RAG chunk stage metadata", ex);
        }
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to deserialize RAG chunk stage metadata", ex);
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private final class StageRowMapper implements RowMapper<RagChunkStage> {

        @Override
        public RagChunkStage mapRow(ResultSet rs, int rowNum) throws SQLException {
            Instant createdAt = rs.getTimestamp("created_at") == null
                    ? Instant.now()
                    : rs.getTimestamp("created_at").toInstant();
            return new RagChunkStage(
                    rs.getString("object_type"),
                    rs.getString("object_id"),
                    rs.getString("document_id"),
                    rs.getInt("chunk_index"),
                    rs.getString("chunk_id"),
                    rs.getString("text"),
                    readJson(rs.getString("metadata")),
                    createdAt);
        }
    }

    private final class IndexedChunkRowMapper implements RowMapper<RagChunkStage> {

        @Override
        public RagChunkStage mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> metadata = readJson(rs.getString("metadata"));
            Object chunkId = metadata.get("chunkId");
            Instant createdAt = rs.getTimestamp("created_at") == null
                    ? Instant.now()
                    : rs.getTimestamp("created_at").toInstant();
            return new RagChunkStage(
                    rs.getString("object_type"),
                    rs.getString("object_id"),
                    text(metadata.get("markdownDocumentId")),
                    rs.getInt("chunk_index"),
                    chunkId == null ? Long.toString(rs.getLong("id")) : chunkId.toString(),
                    rs.getString("text"),
                    metadata,
                    createdAt);
        }
    }
}
