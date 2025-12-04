package studio.one.platform.ai.adapters.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;

import studio.one.platform.data.sqlquery.annotation.SqlStatement;

/**
 * PgVector {@link VectorStorePort} implementation backed by sqlset-defined
 * statements and {@link SqlStatement} injection.
 */
@Component
public class PgVectorStoreAdapterV2 implements VectorStorePort {

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

    @SqlStatement("ai.vector.upsertChunk")
    private String upsertSql;

    @SqlStatement("ai.vector.search")
    private String searchSql;

    @SqlStatement("ai.vector.searchByObject")
    private String searchByObjectSql;

    @SqlStatement("ai.vector.hybridSearch")
    private String hybridSearchSql;

    @SqlStatement("ai.vector.hybridSearchByObject")
    private String hybridSearchByObjectSql;

    @SqlStatement("ai.vector.exists")
    private String existsSql;

    @SqlStatement("ai.vector.listByObject")
    private String listByObjectSql;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;

    public PgVectorStoreAdapterV2(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public void upsert(List<VectorDocument> documents) {
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
        namedParameterJdbcTemplate.batchUpdate(upsertSql, params);
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        PGvector vector = toPgVector(request.embedding());
        return namedParameterJdbcTemplate.query(searchSql, new MapSqlParameterSource()
                .addValue("vector", vector)
                .addValue("limit", request.topK()), ROW_MAPPER);
    }

    @Override
    public List<VectorSearchResult> searchByObject(String objectType, String objectId, VectorSearchRequest request) {
        PGvector vector = toPgVector(request.embedding());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("objectType", normalize(objectType))
                .addValue("objectId", normalize(objectId))
                .addValue("vector", vector)
                .addValue("limit", request.topK());
        return namedParameterJdbcTemplate.query(searchByObjectSql, params, ROW_MAPPER);
    }

    /**
     * 벡터 유사도(semantic similarity) 와 텍스트 기반 검색 점수(lexical relevance) 를 하나의
     * 점수(hybrid) 로 결합해, Semantic + Lexical 하이브리드 검색을 구현.
     * - Semantic Similarity Score (거리 기반).
     * - PostgreSQL Full Text Search(FTS) 정규화 점수 을 활용 query 와 keywords 비교. 키워드가 문서 동사/명사와 잘 맞으면 점수 증가
     * 
     */
    @Override
    public List<VectorSearchResult> hybridSearch(String query, VectorSearchRequest request, double vectorWeight,
            double lexicalWeight) {
        PGvector vector = toPgVector(request.embedding());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vector", vector)
                .addValue("query", query)
                .addValue("vectorWeight", vectorWeight)
                .addValue("lexicalWeight", lexicalWeight)
                .addValue("limit", request.topK());
        return namedParameterJdbcTemplate.query(hybridSearchSql, params, ROW_MAPPER);
    }

    @Override
    public List<VectorSearchResult> hybridSearchByObject(String query, String objectType, String objectId,
            VectorSearchRequest request, double vectorWeight, double lexicalWeight) {
        PGvector vector = toPgVector(request.embedding());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vector", vector)
                .addValue("query", query)
                .addValue("vectorWeight", vectorWeight)
                .addValue("lexicalWeight", lexicalWeight)
                .addValue("objectType", normalize(objectType))
                .addValue("objectId", normalize(objectId))
                .addValue("limit", request.topK());
        return namedParameterJdbcTemplate.query(hybridSearchByObjectSql, params, ROW_MAPPER);
    }

    @Override
    public boolean exists(String objectType, String objectId) {
        Integer count = jdbcTemplate.queryForObject(
                existsSql,
                Integer.class,
                objectType,
                objectId);
        return count != null && count > 0;
    }

    @Override
    public List<VectorSearchResult> listByObject(String objectType, String objectId, Integer limit) {
        int rowLimit = limit == null || limit <= 0 ? Integer.MAX_VALUE : limit;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("objectType", objectType)
                .addValue("objectId", objectId)
                .addValue("limit", rowLimit);
        return namedParameterJdbcTemplate.query(listByObjectSql, params, (rs, rowNum) -> {
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

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value;
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
