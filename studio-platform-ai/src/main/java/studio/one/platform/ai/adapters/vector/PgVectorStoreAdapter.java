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
import java.util.Map;

/**
 * PgVector based implementation of {@link VectorStorePort}.
 */
public class PgVectorStoreAdapter implements VectorStorePort {

    private static final RowMapper<VectorSearchResult> ROW_MAPPER = new RowMapper<>() {
        @Override
        public VectorSearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            String id = rs.getString("id");
            String content = rs.getString("content");
            String metadataJson = rs.getString("metadata");
            double distance = rs.getDouble("distance");
            double score = 1.0d / (1.0d + distance);
            Map<String, Object> metadata = Json.read(metadataJson);
            VectorDocument document = new VectorDocument(id, content, metadata, List.of());
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
        String sql = "INSERT INTO ai_documents(id, content, metadata, embedding) VALUES (:id, :content, CAST(:metadata AS jsonb), :embedding) " +
                "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding";
        List<MapSqlParameterSource> batch = new ArrayList<>(documents.size());
        for (VectorDocument document : documents) {
            batch.add(new MapSqlParameterSource()
                    .addValue("id", document.id())
                    .addValue("content", document.content())
                    .addValue("metadata", Json.write(document.metadata()))
                    .addValue("embedding", toPgVector(document.embedding())));
        }
        MapSqlParameterSource[] params = batch.toArray(MapSqlParameterSource[]::new);
        namedParameterJdbcTemplate.batchUpdate(sql, params);
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        String sql = "SELECT id, content, metadata, (embedding <-> ?) AS distance FROM ai_documents ORDER BY embedding <-> ? ASC LIMIT ?";
        PGvector vector = toPgVector(request.embedding());
        return jdbcTemplate.query(sql, ps -> {
            ps.setObject(1, vector);
            ps.setObject(2, vector);
            ps.setInt(3, request.topK());
        }, ROW_MAPPER);
    }

    private static PGvector toPgVector(List<Double> embedding) {
        float[] values = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            values[i] = embedding.get(i).floatValue();
        }
        return new PGvector(values);
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
