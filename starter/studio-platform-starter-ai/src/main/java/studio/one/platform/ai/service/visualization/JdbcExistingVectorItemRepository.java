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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.VectorItem;

public class JdbcExistingVectorItemRepository implements ExistingVectorItemRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");
    private static final Pattern FILTER_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final List<String> LABEL_KEYS = List.of(
            "sourceName", "title", "filename", "fileName", "name", "headingPath", "sourceRef");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<VectorItem> rowMapper = this::mapItem;
    private final boolean postgres;

    public JdbcExistingVectorItemRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.postgres = JdbcVectorProjectionSql.isPostgres(jdbcTemplate);
    }

    @Override
    public List<VectorItem> findItems(List<String> targetTypes, Map<String, Object> filters) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String targetClause = "";
        if (targetTypes != null && !targetTypes.isEmpty()) {
            targetClause = " AND object_type IN (:targetTypes)";
            params.addValue("targetTypes", targetTypes);
        }
        String filterClause = filterClause(filters, params);
        params.addValue("limit", ExistingVectorItemRepository.DEFAULT_MAX_PROJECTION_ITEMS + 1);
        List<VectorItem> items = jdbcTemplate.query("""
                SELECT id, object_type, object_id, chunk_index, text, embedding, metadata, created_at
                  FROM tb_ai_document_chunk
                 WHERE embedding IS NOT NULL
                """ + targetClause + filterClause + """
                 ORDER BY object_type, object_id, chunk_index, id
                 LIMIT :limit
                """, params, rowMapper);
        return items;
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
        return jdbcTemplate.query("""
                SELECT id, object_type, object_id, chunk_index, text, embedding, metadata, created_at
                 FROM tb_ai_document_chunk
                 WHERE """ + jsonText(null, "chunkId") + """
                    IN (:ids)
                    OR """ + rowVectorItemId("id") + """
                    IN (:ids)
                    OR """ + jsonText(null, "documentId") + """
                    IN (:ids)
                 ORDER BY object_type, object_id, chunk_index, id
                """, new MapSqlParameterSource("ids", ids), rowMapper);
    }

    private VectorItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        long rowId = rs.getLong("id");
        String objectType = rs.getString("object_type");
        String objectId = rs.getString("object_id");
        String text = rs.getString("text");
        Map<String, Object> metadata = readJson(rs.getString("metadata"));
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
        return new VectorItem(
                vectorItemId,
                objectType,
                objectId,
                label,
                text,
                embedding,
                firstText(metadata, "embeddingModel"),
                integer(metadata.get("embeddingDimension"), embedding.isEmpty() ? null : embedding.size()),
                metadata,
                instant(rs.getTimestamp("created_at")));
    }

    private String filterClause(Map<String, Object> filters, MapSqlParameterSource params) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        StringBuilder clause = new StringBuilder();
        int index = 0;
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object expected = entry.getValue();
            if (expected == null) {
                continue;
            }
            String key = entry.getKey();
            if (key == null || !FILTER_KEY_PATTERN.matcher(key).matches()) {
                continue;
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

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String jsonText(String alias, String keyExpression) {
        return JdbcVectorProjectionSql.jsonText(alias, keyExpression, postgres);
    }

    private String rowVectorItemId(String idExpression) {
        return JdbcVectorProjectionSql.rowVectorItemId(idExpression, postgres);
    }
}
