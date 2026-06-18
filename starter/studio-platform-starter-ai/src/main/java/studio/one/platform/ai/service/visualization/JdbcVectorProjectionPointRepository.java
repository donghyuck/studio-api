package studio.one.platform.ai.service.visualization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointView;
import studio.one.platform.ai.core.vector.visualization.VectorVisualizationMetadataSanitizer;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;

public class JdbcVectorProjectionPointRepository implements VectorProjectionPointRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<ProjectionPointView> rowMapper = this::mapView;
    private final boolean postgres;

    public JdbcVectorProjectionPointRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.postgres = JdbcVectorProjectionSql.isPostgres(jdbcTemplate);
    }

    @Override
    public void deleteByProjectionId(String projectionId) {
        jdbcTemplate.update("DELETE FROM tb_ai_vector_projection_point WHERE projection_id = :projectionId",
                new MapSqlParameterSource("projectionId", projectionId));
    }

    @Override
    public void saveAll(List<VectorProjectionPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] params = points.stream()
                .map(point -> new MapSqlParameterSource()
                        .addValue("projectionId", point.projectionId())
                        .addValue("vectorItemId", point.vectorItemId())
                        .addValue("documentChunkId", point.documentChunkId())
                        .addValue("targetType", point.targetType())
                        .addValue("sourceId", point.sourceId())
                        .addValue("label", point.label())
                        .addValue("metadataPreview", writeJson(point.metadataPreview()))
                        .addValue("x", point.x())
                        .addValue("y", point.y())
                        .addValue("clusterId", point.clusterId())
                        .addValue("displayOrder", point.displayOrder())
                        .addValue("createdAt", point.createdAt() == null ? null : Timestamp.from(point.createdAt())))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO tb_ai_vector_projection_point(
                    projection_id, vector_item_id, document_chunk_id, target_type, source_id, label,
                    metadata_preview_json, x, y, cluster_id, display_order, created_at)
                VALUES (
                    :projectionId, :vectorItemId, :documentChunkId, :targetType, :sourceId, :label,
                    :metadataPreview, :x, :y, :clusterId, :displayOrder, :createdAt)
                """, params);
    }

    @Override
    public ProjectionPointPage findPage(
            String projectionId,
            String targetType,
            String clusterId,
            String keyword,
            int limit,
            int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectionId", projectionId)
                .addValue("targetType", targetType)
                .addValue("clusterId", clusterId)
                .addValue("keyword", keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%")
                .addValue("limit", limit)
                .addValue("offset", offset);
        String where = whereClause(targetType, clusterId, keyword);
        Long total = jdbcTemplate.queryForObject(countSql(targetType, keyword, where), params, Long.class);
        List<ProjectionPointView> items = jdbcTemplate.query("""
                SELECT p.vector_item_id, p.target_type, p.source_id, p.label, p.metadata_preview_json,
                       p.x, p.y, p.cluster_id
                  FROM tb_ai_vector_projection_point p
                 WHERE p.projection_id = :projectionId
                """ + where + """
                """ + JdbcVectorProjectionSql.orderByDisplayOrderClause(postgres) + """
                 LIMIT :limit OFFSET :offset
                """, params, rowMapper);
        return new ProjectionPointPage(total == null ? 0L : total, items);
    }

    String countSql(String targetType, String keyword, String where) {
        return """
                SELECT COUNT(*)
                  FROM tb_ai_vector_projection_point p
                 WHERE p.projection_id = :projectionId
                """ + where;
    }

    @Override
    public List<ProjectionPointView> findByVectorItemIds(String projectionId, Collection<String> vectorItemIds) {
        List<String> ids = vectorItemIds == null ? List.of() : vectorItemIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT p.vector_item_id, p.target_type, p.source_id, p.label, p.metadata_preview_json,
                       p.x, p.y, p.cluster_id
                  FROM tb_ai_vector_projection_point p
                 WHERE p.projection_id = :projectionId
                   AND p.vector_item_id IN (:vectorItemIds)
                """, new MapSqlParameterSource()
                .addValue("projectionId", projectionId)
                .addValue("vectorItemIds", ids), rowMapper);
    }

    @Override
    public java.util.Optional<ProjectionPointView> findByVectorItemId(String projectionId, String vectorItemId) {
        return findByVectorItemIds(projectionId, List.of(vectorItemId)).stream().findFirst();
    }

    private String whereClause(String targetType, String clusterId, String keyword) {
        StringBuilder where = new StringBuilder();
        if (targetType != null) {
            where.append(" AND p.target_type = :targetType");
        }
        if (clusterId != null) {
            where.append(" AND p.cluster_id = :clusterId");
        }
        if (keyword != null) {
            where.append("""
                     AND (
                         LOWER(COALESCE(p.source_id, '')) LIKE :keyword
                      OR LOWER(COALESCE(p.label, '')) LIKE :keyword
                     )
                    """);
        }
        return where.toString();
    }

    private ProjectionPointView mapView(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> metadata = readJson(rs.getString("metadata_preview_json"));
        String sourceId = rs.getString("source_id");
        String label = rs.getString("label");
        if (label == null || label.isBlank()) {
            label = label(metadata, sourceId);
        }
        return new ProjectionPointView(
                rs.getString("vector_item_id"),
                rs.getString("target_type"),
                sourceId,
                label,
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getString("cluster_id"),
                VectorVisualizationMetadataSanitizer.sanitize(metadata));
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(VectorVisualizationMetadataSanitizer.sanitize(value));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String label(Map<String, Object> metadata, String fallback) {
        for (String key : List.of("sourceName", "title", "filename", "fileName", "name", "headingPath", "sourceRef")) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return fallback;
    }

}
