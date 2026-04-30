package studio.one.platform.ai.service.visualization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;

public class JdbcVectorProjectionRepository implements VectorProjectionRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<VectorProjection> rowMapper = this::mapProjection;
    private final boolean postgres;

    public JdbcVectorProjectionRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.postgres = isPostgres(jdbcTemplate);
    }

    @Override
    public void save(VectorProjection projection) {
        String filterExpression = postgres ? "CAST(:filters AS jsonb)" : ":filters";
        jdbcTemplate.update("""
                INSERT INTO tb_ai_vector_projection(
                    projection_id, name, algorithm, status, target_types, filter_json,
                    item_count, error_message, created_by, created_at, completed_at)
                VALUES (
                    :projectionId, :name, :algorithm, :status, :targetTypes, %s,
                    :itemCount, :errorMessage, :createdBy, :createdAt, :completedAt)
                """.formatted(filterExpression), params(projection));
    }

    @Override
    public Optional<VectorProjection> findById(String projectionId) {
        List<VectorProjection> items = jdbcTemplate.query("""
                SELECT projection_id, name, algorithm, status, target_types, filter_json,
                       item_count, error_message, created_by, created_at, completed_at
                  FROM tb_ai_vector_projection
                 WHERE projection_id = :projectionId
                """, new MapSqlParameterSource("projectionId", projectionId), rowMapper);
        return items.stream().findFirst();
    }

    @Override
    public List<VectorProjection> findAll(int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT projection_id, name, algorithm, status, target_types, filter_json,
                       item_count, error_message, created_by, created_at, completed_at
                  FROM tb_ai_vector_projection
                 WHERE status <> 'DELETED'
                 ORDER BY created_at DESC
                 LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset), rowMapper);
    }

    @Override
    public void updateStatus(String projectionId, ProjectionStatus status, String errorMessage, Instant completedAt) {
        jdbcTemplate.update("""
                UPDATE tb_ai_vector_projection
                   SET status = :status,
                       error_message = :errorMessage,
                       completed_at = :completedAt
                 WHERE projection_id = :projectionId
                """, new MapSqlParameterSource()
                .addValue("projectionId", projectionId)
                .addValue("status", status.name())
                .addValue("errorMessage", errorMessage)
                .addValue("completedAt", timestamp(completedAt)));
    }

    @Override
    public void markCompleted(String projectionId, int itemCount, Instant completedAt) {
        jdbcTemplate.update("""
                UPDATE tb_ai_vector_projection
                   SET status = 'COMPLETED',
                       item_count = :itemCount,
                       error_message = NULL,
                       completed_at = :completedAt
                 WHERE projection_id = :projectionId
                """, new MapSqlParameterSource()
                .addValue("projectionId", projectionId)
                .addValue("itemCount", itemCount)
                .addValue("completedAt", timestamp(completedAt)));
    }

    private MapSqlParameterSource params(VectorProjection projection) {
        return new MapSqlParameterSource()
                .addValue("projectionId", projection.projectionId())
                .addValue("name", projection.name())
                .addValue("algorithm", projection.algorithm().name())
                .addValue("status", projection.status().name())
                .addValue("targetTypes", String.join(",", projection.targetTypes()))
                .addValue("filters", writeJson(projection.filters()))
                .addValue("itemCount", projection.itemCount())
                .addValue("errorMessage", projection.errorMessage())
                .addValue("createdBy", projection.createdBy())
                .addValue("createdAt", timestamp(projection.createdAt()))
                .addValue("completedAt", timestamp(projection.completedAt()));
    }

    private VectorProjection mapProjection(ResultSet rs, int rowNum) throws SQLException {
        return new VectorProjection(
                rs.getString("projection_id"),
                rs.getString("name"),
                ProjectionAlgorithm.valueOf(rs.getString("algorithm")),
                ProjectionStatus.valueOf(rs.getString("status")),
                readTargetTypes(rs.getString("target_types")),
                readJson(rs.getString("filter_json")),
                rs.getInt("item_count"),
                rs.getString("error_message"),
                rs.getString("created_by"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("completed_at")));
    }

    private List<String> readTargetTypes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid projection filter JSON", ex);
        }
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE).entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean isPostgres(NamedParameterJdbcTemplate template) {
        try {
            return Boolean.TRUE.equals(template.getJdbcOperations().execute((ConnectionCallback<Boolean>) connection -> {
                String productName = connection.getMetaData().getDatabaseProductName();
                return productName != null && productName.toLowerCase(java.util.Locale.ROOT).contains("postgres");
            }));
        } catch (RuntimeException ex) {
            return true;
        }
    }
}
