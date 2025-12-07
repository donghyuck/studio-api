package studio.one.platform.ai.adapters.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * PgVector based implementation of {@link VectorStorePort}.
 */
public class PgVectorStoreAdapter implements VectorStorePort {

    private static final RowMapper<VectorSearchResult> ROW_MAPPER = new RowMapper<>() {
        @Override
        public VectorSearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            String objectId = rs.getString("object_id");
            String content = rs.getString("text");
            String metadataJson = rs.getString("metadata");
            double distance = rs.getDouble("distance");
            double score = 1.0d / (1.0d + distance);
            Map<String, Object> metadata = Json.read(metadataJson);
            String documentId = Objects.toString(metadata.getOrDefault("documentId", objectId), objectId);
            VectorDocument document = new VectorDocument(documentId, content, metadata, List.of());
            return new VectorSearchResult(document, score);
        }
    };

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;

    public PgVectorStoreAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public void upsert(List<VectorDocument> documents) {
        String sql = """
                INSERT INTO tb_ai_document_chunk(object_type, object_id, chunk_index, text, metadata, embedding)
                VALUES (:objectType, :objectId, :chunkIndex, :text, CAST(:metadata AS jsonb), :embedding)
                ON CONFLICT (object_type, object_id, chunk_index)
                DO UPDATE SET text = EXCLUDED.text, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding
                """;
        List<MapSqlParameterSource> batch = new ArrayList<>(documents.size());
        for (VectorDocument document : documents) {
            Map<String, Object> metadata = withDocumentId(document);
            batch.add(new MapSqlParameterSource()
                    .addValue("objectType", resolveObjectType(metadata))
                    .addValue("objectId", resolveObjectId(metadata, document.id()))
                    .addValue("chunkIndex", resolveChunkIndex(metadata))
                    .addValue("text", document.content())
                    .addValue("metadata", Json.write(metadata))
                    .addValue("embedding", toPgVector(document.embedding())));
        }
        MapSqlParameterSource[] params = batch.toArray(MapSqlParameterSource[]::new);
        namedParameterJdbcTemplate.batchUpdate(sql, params);
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        String sql = """
                SELECT object_id, text, metadata, (embedding <-> :vector) AS distance
                  FROM tb_ai_document_chunk
                 ORDER BY embedding <-> :vector ASC
                 LIMIT :limit
                """;
        PGvector vector = toPgVector(request.embedding());
        return namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("vector", vector)
                .addValue("limit", request.topK()), ROW_MAPPER);
    }

    @Override
    public List<VectorSearchResult> searchByObject(String objectType, String objectId, VectorSearchRequest request) {
        String sql = """
                SELECT object_id, text, metadata, (embedding <-> :vector) AS distance
                  FROM tb_ai_document_chunk
                 WHERE (:objectType IS NULL OR object_type = :objectType)
                   AND (:objectId IS NULL OR object_id = :objectId)
                 ORDER BY embedding <-> :vector ASC
                 LIMIT :limit
                """;
        PGvector vector = toPgVector(request.embedding());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("objectType", (objectType == null || objectType.isBlank()) ? null : objectType)
                .addValue("objectId", (objectId == null || objectId.isBlank()) ? null : objectId)
                .addValue("vector", vector)
                .addValue("limit", request.topK());

        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            String objId = rs.getString("object_id");
            String content = rs.getString("text");
            String metadataJson = rs.getString("metadata");
            double distance = rs.getDouble("distance");
            double score = 1.0d / (1.0d + distance);
            Map<String, Object> metadata = Json.read(metadataJson);
            String documentId = Objects.toString(metadata.getOrDefault("documentId", objId), objId);
            VectorDocument document = new VectorDocument(documentId, content, metadata, List.of());
            return new VectorSearchResult(document, score);
        });
    }

    @Override
    public List<VectorSearchResult> hybridSearch(String query, VectorSearchRequest request, double vectorWeight,
            double lexicalWeight) {
        String sql = """
                SELECT object_id, text, metadata,
                       (embedding <-> :vector) AS distance,
                       ts_rank_cd(to_tsvector('simple', text), plainto_tsquery(:query)) AS bm25,
                       ((embedding <-> :vector) * :vectorWeight) - (COALESCE(ts_rank_cd(to_tsvector('simple', text), plainto_tsquery(:query)),0) * :lexicalWeight) AS hybrid
                  FROM tb_ai_document_chunk
                 ORDER BY hybrid ASC
                 LIMIT :limit
                """;
        PGvector vector = toPgVector(request.embedding());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vector", vector)
                .addValue("query", query)
                .addValue("vectorWeight", vectorWeight)
                .addValue("lexicalWeight", lexicalWeight)
                .addValue("limit", request.topK());
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            String objId = rs.getString("object_id");
            String content = rs.getString("text");
            String metadataJson = rs.getString("metadata");
            double distance = rs.getDouble("distance");
            double score = 1.0d / (1.0d + distance);
            Map<String, Object> metadata = Json.read(metadataJson);
            String documentId = Objects.toString(metadata.getOrDefault("documentId", objId), objId);
            VectorDocument document = new VectorDocument(documentId, content, metadata, List.of());
            return new VectorSearchResult(document, score);
        });
    }

    @Override
    public List<VectorSearchResult> hybridSearchByObject(String query, String objectType, String objectId,
            VectorSearchRequest request, double vectorWeight, double lexicalWeight) {
        String sql = """
                SELECT object_id, text, metadata,
                       (embedding <-> :vector) AS distance,
                       ts_rank_cd(to_tsvector('simple', text), plainto_tsquery(:query)) AS bm25,
                       ((embedding <-> :vector) * :vectorWeight) - (COALESCE(ts_rank_cd(to_tsvector('simple', text), plainto_tsquery(:query)),0) * :lexicalWeight) AS hybrid
                  FROM tb_ai_document_chunk
                 WHERE (:objectType IS NULL OR object_type = :objectType)
                   AND (:objectId IS NULL OR object_id = :objectId)
                 ORDER BY hybrid ASC
                 LIMIT :limit
                """;
        PGvector vector = toPgVector(request.embedding());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vector", vector)
                .addValue("query", query)
                .addValue("vectorWeight", vectorWeight)
                .addValue("lexicalWeight", lexicalWeight)
                .addValue("objectType", (objectType == null || objectType.isBlank()) ? null : objectType)
                .addValue("objectId", (objectId == null || objectId.isBlank()) ? null : objectId)
                .addValue("limit", request.topK());
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            String objId = rs.getString("object_id");
            String content = rs.getString("text");
            String metadataJson = rs.getString("metadata");
            double distance = rs.getDouble("distance");
            double score = 1.0d / (1.0d + distance);
            Map<String, Object> metadata = Json.read(metadataJson);
            String documentId = Objects.toString(metadata.getOrDefault("documentId", objId), objId);
            VectorDocument document = new VectorDocument(documentId, content, metadata, List.of());
            return new VectorSearchResult(document, score);
        });
    }

    @Override
    public boolean exists(String objectType, String objectId) {
        String sql = """
                SELECT COUNT(*)
                  FROM tb_ai_document_chunk
                 WHERE object_type = ? AND object_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                objectType,
                objectId);

        return count != null && count > 0;
    }

    @Override
    public Map<String, Object> getMetadata(String objectType, String objectId) {
        String sql = """
                SELECT metadata
                  FROM tb_ai_document_chunk
                 WHERE object_type = :objectType AND object_id = :objectId
                 ORDER BY chunk_index
                 LIMIT 1
                """;
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.query(sql,
                new MapSqlParameterSource()
                        .addValue("objectType", objectType)
                        .addValue("objectId", objectId),
                (rs, rowNum) -> Json.read(rs.getString("metadata")));
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0) == null ? Map.of() : Map.copyOf(rows.get(0));
    }

    @Override
    public List<VectorSearchResult> listByObject(String objectType, String objectId, Integer limit) {
        String sql = """
                SELECT object_id, text, metadata
                  FROM tb_ai_document_chunk
                 WHERE object_type = :objectType AND object_id = :objectId
                 ORDER BY chunk_index
                 LIMIT :limit
                """;
        int rowLimit = limit == null || limit <= 0 ? Integer.MAX_VALUE : limit;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("objectType", objectType)
                .addValue("objectId", objectId)
                .addValue("limit", rowLimit);

        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            String objId = rs.getString("object_id");
            String content = rs.getString("text");
            String metadataJson = rs.getString("metadata");
            Map<String, Object> metadata = Json.read(metadataJson);
            String documentId = Objects.toString(metadata.getOrDefault("documentId", objId), objId);
            VectorDocument document = new VectorDocument(documentId, content, metadata, List.of());
            return new VectorSearchResult(document, 1.0d);
        });
    }

    private static PGvector toPgVector(List<Double> embedding) {
        float[] values = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            values[i] = embedding.get(i).floatValue();
        }
        return new PGvector(values);
    }

    private static String resolveObjectType(Map<String, Object> metadata) {
        Object value = metadata.getOrDefault("objectType", "DEFAULT");
        return Objects.toString(value, "DEFAULT");
    }

    private static String resolveObjectId(Map<String, Object> metadata, String fallback) {
        Object value = metadata.get("objectId");
        if (value != null && !Objects.toString(value, "").isBlank()) {
            return Objects.toString(value);
        }
        return fallback;
    }

    private static int resolveChunkIndex(Map<String, Object> metadata) {
        Object value = metadata.getOrDefault("chunkOrder", 0);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static Map<String, Object> withDocumentId(VectorDocument document) {
        Map<String, Object> metadata = new HashMap<>(document.metadata());
        metadata.putIfAbsent("documentId", document.id());
        return metadata;
    }

    private static final class Json {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        private Json() {
        }

        private static String write(Map<String, Object> metadata) {
            try {
                return objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize metadata", e);
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> read(String json) {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            try {
                return objectMapper.readValue(json, Map.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize metadata", e);
            }
        }
    }
}
