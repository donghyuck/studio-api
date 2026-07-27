package studio.one.platform.ai.service.visualization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.ProjectionVector;
import studio.one.platform.ai.core.vector.visualization.ProjectionSamplingStrategy;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionScope;

public class JdbcExistingVectorItemRepository implements ExistingVectorItemRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");
    private static final Pattern FILTER_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final int PROJECTION_FETCH_SIZE = 100;
    private static final List<String> LABEL_KEYS = List.of(
            "sourceName", "title", "filename", "fileName", "name", "headingPath", "sourceRef");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<VectorItem> rowMapper = this::mapItem;
    private final RowMapper<ProjectionVector> projectionVectorRowMapper = this::mapProjectionVector;
    private final boolean postgres;

    public JdbcExistingVectorItemRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.postgres = JdbcVectorProjectionSql.isPostgres(jdbcTemplate);
        this.jdbcTemplate.getJdbcTemplate().setFetchSize(PROJECTION_FETCH_SIZE);
    }

    @Override
    public List<VectorItem> findItems(List<String> targetTypes, Map<String, Object> filters) {
        return findItems(
                new VectorProjectionScope(targetTypes, filters),
                ProjectionSamplingStrategy.HEAD,
                ExistingVectorItemRepository.DEFAULT_MAX_PROJECTION_ITEMS + 1);
    }

    @Override
    public long count(VectorProjectionScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = scopeClause(scope, params);
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM tb_ai_document_chunk
                 WHERE embedding IS NOT NULL
                """ + where, params, Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public List<VectorItem> findItems(
            VectorProjectionScope scope,
            ProjectionSamplingStrategy samplingStrategy,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = scopeClause(scope, params);
        params.addValue("limit", limit);
        ProjectionSamplingStrategy strategy = samplingStrategy == null
                ? ProjectionSamplingStrategy.STRATIFIED
                : samplingStrategy;
        String columns = "id, object_type, object_id, chunk_index, NULL AS text, embedding, "
                + labelMetadataExpression() + " AS metadata, created_at";
        if (strategy == ProjectionSamplingStrategy.STRATIFIED) {
            return jdbcTemplate.query("""
                    SELECT id, object_type, object_id, chunk_index, text, embedding, metadata, created_at
                      FROM (
                            SELECT %s,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY object_type, object_id
                                       ORDER BY chunk_index, id
                                   ) AS sample_rank,
                                   COUNT(*) OVER (
                                       PARTITION BY object_type, object_id
                                   ) AS partition_count
                              FROM tb_ai_document_chunk
                             WHERE embedding IS NOT NULL
                            %s
                           ) sampled
                     ORDER BY FLOOR(((sample_rank - 1) * :limit) / NULLIF(partition_count, 0)),
                              object_type, object_id, sample_rank, chunk_index, id
                     LIMIT :limit
                    """.formatted(columns, where), params, rowMapper);
        }
        String orderBy = strategy == ProjectionSamplingStrategy.RANDOM
                ? JdbcVectorProjectionSql.randomOrder(postgres)
                : "object_type, object_id, chunk_index, id";
        return jdbcTemplate.query("""
                SELECT id, object_type, object_id, chunk_index, text, embedding, metadata, created_at
                  FROM tb_ai_document_chunk
                 WHERE embedding IS NOT NULL
                """ + where + """
                 ORDER BY """ + orderBy + """
                 LIMIT :limit
                """, params, rowMapper);
    }

    @Override
    public List<ProjectionVector> findProjectionVectors(
            VectorProjectionScope scope,
            ProjectionSamplingStrategy samplingStrategy,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = scopeClause(scope, params);
        params.addValue("limit", limit);
        ProjectionSamplingStrategy strategy = samplingStrategy == null
                ? ProjectionSamplingStrategy.STRATIFIED
                : samplingStrategy;
        String columns = "id, object_type, object_id, chunk_index, embedding, "
                + labelMetadataExpression() + " AS metadata, created_at";
        if (strategy == ProjectionSamplingStrategy.STRATIFIED) {
            return jdbcTemplate.query("""
                    SELECT id, object_type, object_id, chunk_index, embedding, metadata, created_at
                      FROM (
                            SELECT %s,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY object_type, object_id
                                       ORDER BY chunk_index, id
                                   ) AS sample_rank,
                                   COUNT(*) OVER (
                                       PARTITION BY object_type, object_id
                                   ) AS partition_count
                              FROM tb_ai_document_chunk
                             WHERE embedding IS NOT NULL
                            %s
                           ) sampled
                     ORDER BY FLOOR(((sample_rank - 1) * :limit) / NULLIF(partition_count, 0)),
                              object_type, object_id, sample_rank, chunk_index, id
                     LIMIT :limit
                    """.formatted(columns, where), params, projectionVectorRowMapper);
        }
        String orderBy = strategy == ProjectionSamplingStrategy.RANDOM
                ? JdbcVectorProjectionSql.randomOrder(postgres)
                : "object_type, object_id, chunk_index, id";
        return jdbcTemplate.query("""
                SELECT id, object_type, object_id, chunk_index, embedding, metadata, created_at
                  FROM tb_ai_document_chunk
                 WHERE embedding IS NOT NULL
                """ + where + """
                 ORDER BY """ + orderBy + """
                 LIMIT :limit
                """, params, projectionVectorRowMapper);
    }

    @Override
    public Optional<VectorItem> findByVectorItemId(String vectorItemId) {
        return findByVectorItemIds(List.of(vectorItemId)).stream().findFirst();
    }

    @Override
    public List<VectorItem> findByVectorItemIds(Collection<String> vectorItemIds) {
        List<String> ids = vectorItemIds == null ? List.of() : vectorItemIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        List<Long> rowIds = new ArrayList<>();
        List<String> otherIds = new ArrayList<>();
        for (String id : ids) {
            if (id.startsWith("row-")) {
                try {
                    rowIds.add(Long.valueOf(id.substring(4)));
                } catch (NumberFormatException e) {
                    otherIds.add(id);
                }
            } else {
                otherIds.add(id);
            }
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder();
        Map<String, Long> projectedRowIds = documentChunkIds(otherIds);
        if (!projectedRowIds.isEmpty()) {
            rowIds.addAll(projectedRowIds.values());
            otherIds.removeIf(projectedRowIds::containsKey);
        }

        sql.append("SELECT id, object_type, object_id, chunk_index, text, NULL AS embedding, embedding_dimension, metadata, created_at ");
        sql.append("FROM tb_ai_document_chunk WHERE ");

        List<String> clauses = new ArrayList<>();
        if (!rowIds.isEmpty()) {
            clauses.add("id IN (:rowIds)");
            params.addValue("rowIds", rowIds);
        }
        if (!otherIds.isEmpty()) {
            clauses.add("(" + jsonText(null, "chunkId") + " IN (:otherIds) OR "
                    + jsonText(null, "documentId") + " IN (:otherIds))");
            params.addValue("otherIds", otherIds);
        }

        sql.append(String.join(" OR ", clauses));
        sql.append(" ORDER BY object_type, object_id, chunk_index, id");

        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }

    private Map<String, Long> documentChunkIds(List<String> vectorItemIds) {
        if (vectorItemIds == null || vectorItemIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> values = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT vector_item_id, document_chunk_id
                  FROM tb_ai_vector_projection_point
                 WHERE vector_item_id IN (:vectorItemIds)
                   AND document_chunk_id IS NOT NULL
                """, new MapSqlParameterSource("vectorItemIds", vectorItemIds),
                (RowCallbackHandler) rs -> values.putIfAbsent(
                        rs.getString("vector_item_id"),
                        rs.getLong("document_chunk_id")));
        return values;
    }

    private VectorItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        long rowId = rs.getLong("id");
        String objectType = rs.getString("object_type");
        String objectId = rs.getString("object_id");
        String text = rs.getString("text");
        Map<String, Object> metadata = readJson(rs.getString("metadata"));
        metadata.putIfAbsent("_documentChunkId", rowId);
        metadata.putIfAbsent("_vectorRowId", "row-" + rowId);
        metadata.putIfAbsent("objectType", objectType);
        metadata.putIfAbsent("objectId", objectId);
        metadata.putIfAbsent("chunkIndex", rs.getInt("chunk_index"));
        String rowVectorItemId = "row-" + rowId;
        String vectorItemId = firstText(metadata, VectorRecord.KEY_CHUNK_ID);
        if (vectorItemId == null) {
            vectorItemId = rowVectorItemId;
        }
        String label = label(metadata, objectId);
        List<Double> embedding = parseEmbedding(rs.getObject("embedding"));
        Integer embeddingDimension = integer(rs.getObject("embedding_dimension"), embedding.isEmpty() ? null : embedding.size());
        return new VectorItem(
                vectorItemId,
                objectType,
                objectId,
                label,
                text,
                embedding,
                firstText(metadata, "embeddingModel"),
                integer(metadata.get("embeddingDimension"), embeddingDimension),
                metadata,
                instant(rs.getTimestamp("created_at")));
    }

    private String scopeClause(VectorProjectionScope scope, MapSqlParameterSource params) {
        StringBuilder clause = new StringBuilder();
        if (!scope.targetTypes().isEmpty()) {
            clause.append(" AND object_type IN (:targetTypes)");
            params.addValue("targetTypes", scope.targetTypes());
        }
        Map<String, Object> filters = scope.filters();
        Object attachmentId = filters.get("attachmentId");
        Object objectId = filters.get("objectId");
        if (attachmentId != null) {
            clause.append(" AND object_type = :attachmentObjectType AND object_id = :attachmentId");
            params.addValue("attachmentObjectType", "attachment");
            params.addValue("attachmentId", String.valueOf(attachmentId));
        } else if (objectId != null) {
            clause.append(" AND object_id = :objectId");
            params.addValue("objectId", String.valueOf(objectId));
        }
        Integer chunkIndexFrom = integerFilter(filters.get("chunkIndexFrom"), "chunkIndexFrom");
        Integer chunkIndexTo = integerFilter(filters.get("chunkIndexTo"), "chunkIndexTo");
        if (chunkIndexFrom != null) {
            clause.append(" AND chunk_index >= :chunkIndexFrom");
            params.addValue("chunkIndexFrom", chunkIndexFrom);
        }
        if (chunkIndexTo != null) {
            clause.append(" AND chunk_index <= :chunkIndexTo");
            params.addValue("chunkIndexTo", chunkIndexTo);
        }
        int index = 0;
        for (Map.Entry<String, Object> entry : scope.filters().entrySet()) {
            Object expected = entry.getValue();
            if (expected == null) {
                continue;
            }
            String key = entry.getKey();
            if (key == null) {
                throw new IllegalArgumentException("Projection filter key must not be null");
            }
            if (List.of("attachmentId", "objectId", "chunkIndexFrom", "chunkIndexTo").contains(key)) {
                continue;
            }
            if (!FILTER_KEY_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException("Unsupported projection filter key: " + key);
            }
            String keyParam = "filterKey" + index;
            String valueParam = "filterValue" + index;
            clause.append(" AND ").append(jsonText(null, ":" + keyParam)).append(" = :").append(valueParam);
            params.addValue(keyParam, key);
            params.addValue(valueParam, String.valueOf(expected));
            index++;
        }
        return clause.toString();
    }

    private Integer integerFilter(Object value, String name) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer", ex);
        }
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(value, MAP_TYPE));
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String label(Map<String, Object> metadata, String fallback) {
        for (String key : LABEL_KEYS) {
            String value = firstText(metadata, key);
            if (value != null) {
                return value;
            }
        }
        return fallback;
    }

    private String firstText(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = value instanceof Iterable<?> iterable ? join(iterable) : value.toString();
        text = text.trim();
        return text.isBlank() ? null : text;
    }

    private String join(Iterable<?> values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (text.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" > ");
            }
            builder.append(text);
        }
        return builder.toString();
    }

    private Integer integer(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private ProjectionVector mapProjectionVector(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("id");
        String objectType = rs.getString("object_type");
        String objectId = rs.getString("object_id");
        Integer chunkIndex = integer(rs.getObject("chunk_index"), null);
        Map<String, Object> metadata = readJson(rs.getString("metadata"));
        double[] embedding = parseEmbeddingArray(rs.getObject("embedding"));
        String vectorItemId = projectionVectorItemId(metadata, id);
        String fallback = chunkIndex == null ? objectId : objectId + "#" + chunkIndex;
        return new ProjectionVector(
                vectorItemId,
                Long.valueOf(id),
                objectType,
                objectId,
                label(metadata, fallback),
                metadata,
                embedding,
                null,
                embedding.length,
                instant(rs.getTimestamp("created_at")));
    }

    String projectionVectorItemId(Map<String, Object> metadata, String id) {
        String vectorItemId = firstText(metadata, VectorRecord.KEY_CHUNK_ID);
        return vectorItemId == null ? "row-" + id : vectorItemId;
    }

    private List<Double> parseEmbedding(Object value) {
        if (value == null) {
            return List.of();
        }
        String text = value.toString();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(Double.valueOf(matcher.group()));
        }
        return values;
    }

    private double[] parseEmbeddingArray(Object value) {
        if (value == null) {
            return new double[0];
        }
        String text = value.toString();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        double[] values = new double[256];
        int size = 0;
        while (matcher.find()) {
            if (size == values.length) {
                values = java.util.Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = Double.parseDouble(matcher.group());
        }
        return java.util.Arrays.copyOf(values, size);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String jsonText(String alias, String keyExpression) {
        return JdbcVectorProjectionSql.jsonText(alias, keyExpression, postgres);
    }

    private String labelMetadataExpression() {
        if (postgres) {
            return "jsonb_build_object("
                    + "'chunkId', metadata -> 'chunkId', "
                    + "'sourceName', metadata -> 'sourceName', "
                    + "'title', metadata -> 'title', "
                    + "'filename', metadata -> 'filename', "
                    + "'fileName', metadata -> 'fileName', "
                    + "'name', metadata -> 'name', "
                    + "'headingPath', metadata -> 'headingPath', "
                    + "'sourceRef', metadata -> 'sourceRef')";
        }

        return "JSON_OBJECT("
                + "'chunkId', JSON_EXTRACT(metadata, '$.chunkId'), "
                + "'sourceName', JSON_EXTRACT(metadata, '$.sourceName'), "
                + "'title', JSON_EXTRACT(metadata, '$.title'), "
                + "'filename', JSON_EXTRACT(metadata, '$.filename'), "
                + "'fileName', JSON_EXTRACT(metadata, '$.fileName'), "
                + "'name', JSON_EXTRACT(metadata, '$.name'), "
                + "'headingPath', JSON_EXTRACT(metadata, '$.headingPath'), "
                + "'sourceRef', JSON_EXTRACT(metadata, '$.sourceRef'))";
    }

}
