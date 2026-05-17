package studio.one.platform.ai.adapters.vector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorChunkParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorHybridSearchParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMapper;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMetadataEqualsCriterion;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMetadataInCriterion;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorSearchParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorSearchRow;

public final class PgVectorJdbcMapper implements PgVectorMapper {

    private static final String UPSERT_CHUNK_SQL = """
            INSERT INTO tb_ai_document_chunk(object_type, object_id, chunk_index, text, metadata, embedding)
            VALUES (:objectType, :objectId, :chunkIndex, :text, CAST(:metadata AS jsonb), :embedding)
            ON CONFLICT (object_type, object_id, chunk_index)
            DO UPDATE SET text = EXCLUDED.text, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding
            """;
    private static final String SEARCH_SQL = """
            SELECT id, object_id, text, metadata, (embedding <-> :vector) AS distance
              FROM tb_ai_document_chunk
             ORDER BY embedding <-> :vector ASC
             LIMIT :limit
            """;
    private static final String DELETE_BY_OBJECT_SQL = """
            DELETE FROM tb_ai_document_chunk
             WHERE object_type = :objectType AND object_id = :objectId
            """;
    private static final String SEARCH_BY_OBJECT_SQL = """
            SELECT id, object_id, text, metadata, (embedding <-> :vector) AS distance
              FROM tb_ai_document_chunk
             WHERE (CAST(:objectType AS varchar) IS NULL OR object_type = CAST(:objectType AS varchar))
               AND (CAST(:objectId AS varchar) IS NULL OR object_id = CAST(:objectId AS varchar))
             ORDER BY embedding <-> :vector ASC
             LIMIT :limit
            """;
    private static final String HYBRID_SEARCH_SQL = """
            SELECT id, object_id, text, metadata,
                   (embedding <-> :vector) AS distance,
                   ts_rank_cd(to_tsvector('simple', text || ' ' || COALESCE(metadata->>'keywordsText','')), plainto_tsquery(:query)) AS bm25,
                   ((embedding <-> :vector) * :vectorWeight) - (COALESCE(ts_rank_cd(to_tsvector('simple', text || ' ' || COALESCE(metadata->>'keywordsText','')), plainto_tsquery(:query)),0) * :lexicalWeight) AS hybrid
              FROM tb_ai_document_chunk
             ORDER BY hybrid ASC
             LIMIT :limit
            """;
    private static final String HYBRID_SEARCH_BY_OBJECT_SQL = """
            SELECT id, object_id, text, metadata,
                   (embedding <-> :vector) AS distance,
                   ts_rank_cd(to_tsvector('simple', text || ' ' || COALESCE(metadata->>'keywordsText','')), plainto_tsquery(:query)) AS bm25,
                   ((embedding <-> :vector) * :vectorWeight) - (COALESCE(ts_rank_cd(to_tsvector('simple', text || ' ' || COALESCE(metadata->>'keywordsText','')), plainto_tsquery(:query)),0) * :lexicalWeight) AS hybrid
              FROM tb_ai_document_chunk
             WHERE (CAST(:objectType AS varchar) IS NULL OR object_type = CAST(:objectType AS varchar))
               AND (CAST(:objectId AS varchar) IS NULL OR object_id = CAST(:objectId AS varchar))
             ORDER BY hybrid ASC
             LIMIT :limit
            """;
    private static final String EXISTS_SQL = """
            SELECT COUNT(*)
              FROM tb_ai_document_chunk
             WHERE object_type = :objectType AND object_id = :objectId
            """;
    private static final String LIST_BY_OBJECT_SQL = """
            SELECT object_id, text, metadata, NULL::double precision AS distance
              FROM tb_ai_document_chunk
             WHERE object_type = :objectType AND object_id = :objectId
             ORDER BY chunk_index
             LIMIT :limit
            """;
    private static final String LIST_BY_OBJECT_PAGE_SQL = """
            SELECT object_id, text, metadata, NULL::double precision AS distance
              FROM tb_ai_document_chunk
             WHERE object_type = :objectType AND object_id = :objectId
             ORDER BY chunk_index
             LIMIT :limit OFFSET :offset
            """;
    private static final String LIST_BY_OBJECT_PAGE_FILTERED_SQL = """
            SELECT object_id, text, metadata, NULL::double precision AS distance
              FROM tb_ai_document_chunk
             WHERE object_type = :objectType AND object_id = :objectId
               AND (:documentId IS NULL OR metadata->>'documentId' = :documentId)
               AND (:query IS NULL OR (
                    LOWER(text) LIKE :queryPattern
                    OR LOWER(COALESCE(metadata->>'chunkId', '')) LIKE :queryPattern
                    OR LOWER(COALESCE(metadata->>'documentId', '')) LIKE :queryPattern
                    OR LOWER(COALESCE(metadata->>'sourceDocumentId', '')) LIKE :queryPattern
                    OR LOWER(COALESCE(metadata->>'headingPath', '')) LIKE :queryPattern
                    OR LOWER(COALESCE(metadata->>'section', '')) LIKE :queryPattern
               ))
             ORDER BY chunk_index
             LIMIT :limit OFFSET :offset
            """;
    private static final String METADATA_BY_OBJECT_SQL = """
            SELECT metadata
              FROM tb_ai_document_chunk
             WHERE object_type = :objectType AND object_id = :objectId
             ORDER BY chunk_index
             LIMIT 1
            """;

    private static final RowMapper<PgVectorSearchRow> ROW_MAPPER = new RowMapper<>() {
        @Override
        public PgVectorSearchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            PgVectorSearchRow row = new PgVectorSearchRow();
            try {
                long id = rs.getLong("id");
                if (!rs.wasNull()) {
                    row.setId(id);
                }
            } catch (SQLException ignored) {
                // List queries do not select the physical row id.
            }
            row.setObjectId(rs.getString("object_id"));
            row.setText(rs.getString("text"));
            row.setMetadata(rs.getString("metadata"));
            try {
                double distance = rs.getDouble("distance");
                if (!rs.wasNull()) {
                    row.setDistance(distance);
                }
            } catch (SQLException ignored) {
                // List queries do not calculate vector distance.
            }
            return row;
        }
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PgVectorJdbcMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate"));
    }

    @Override
    public int upsertChunk(PgVectorChunkParameter parameter) {
        return jdbcTemplate.update(UPSERT_CHUNK_SQL, chunkParams(parameter));
    }

    @Override
    public List<PgVectorSearchRow> search(PgVectorSearchParameter parameter) {
        return jdbcTemplate.query(filteredSql(SEARCH_SQL, parameter), searchParams(parameter), ROW_MAPPER);
    }

    @Override
    public int deleteByObject(String objectType, String objectId) {
        return jdbcTemplate.update(DELETE_BY_OBJECT_SQL, objectParams(objectType, objectId));
    }

    @Override
    public List<PgVectorSearchRow> searchByObject(PgVectorSearchParameter parameter) {
        return jdbcTemplate.query(filteredSql(SEARCH_BY_OBJECT_SQL, parameter), searchParams(parameter), ROW_MAPPER);
    }

    @Override
    public List<PgVectorSearchRow> hybridSearch(PgVectorHybridSearchParameter parameter) {
        return jdbcTemplate.query(filteredSql(HYBRID_SEARCH_SQL, parameter), hybridSearchParams(parameter), ROW_MAPPER);
    }

    @Override
    public List<PgVectorSearchRow> hybridSearchByObject(PgVectorHybridSearchParameter parameter) {
        return jdbcTemplate.query(
                filteredSql(HYBRID_SEARCH_BY_OBJECT_SQL, parameter),
                hybridSearchParams(parameter),
                ROW_MAPPER);
    }

    @Override
    public int exists(String objectType, String objectId) {
        Integer count = jdbcTemplate.queryForObject(EXISTS_SQL, objectParams(objectType, objectId), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<PgVectorSearchRow> listByObject(String objectType, String objectId, int limit) {
        return jdbcTemplate.query(LIST_BY_OBJECT_SQL, objectParams(objectType, objectId)
                .addValue("limit", limit), ROW_MAPPER);
    }

    @Override
    public List<PgVectorSearchRow> listByObjectPage(String objectType, String objectId, int offset, int limit) {
        return jdbcTemplate.query(LIST_BY_OBJECT_PAGE_SQL, objectParams(objectType, objectId)
                .addValue("offset", offset)
                .addValue("limit", limit), ROW_MAPPER);
    }

    @Override
    public List<PgVectorSearchRow> listByObjectPageFiltered(
            String objectType,
            String objectId,
            String documentId,
            String query,
            int offset,
            int limit) {
        return jdbcTemplate.query(LIST_BY_OBJECT_PAGE_FILTERED_SQL, objectParams(objectType, objectId)
                .addValue("documentId", normalize(documentId))
                .addValue("query", normalize(query))
                .addValue("queryPattern", queryPattern(query))
                .addValue("offset", offset)
                .addValue("limit", limit), ROW_MAPPER);
    }

    @Override
    public String metadataByObject(String objectType, String objectId) {
        List<String> rows = jdbcTemplate.query(
                METADATA_BY_OBJECT_SQL,
                objectParams(objectType, objectId),
                (rs, rowNum) -> rs.getString("metadata"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static MapSqlParameterSource chunkParams(PgVectorChunkParameter parameter) {
        return new MapSqlParameterSource()
                .addValue("objectType", parameter.getObjectType())
                .addValue("objectId", parameter.getObjectId())
                .addValue("chunkIndex", parameter.getChunkIndex())
                .addValue("text", parameter.getText())
                .addValue("metadata", parameter.getMetadata())
                .addValue("embedding", parameter.getEmbedding());
    }

    private static MapSqlParameterSource objectParams(String objectType, String objectId) {
        return new MapSqlParameterSource()
                .addValue("objectType", objectType)
                .addValue("objectId", objectId);
    }

    private static String queryPattern(String query) {
        String normalized = normalize(query);
        return normalized == null ? null : "%" + normalized.toLowerCase(java.util.Locale.ROOT) + "%";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MapSqlParameterSource searchParams(PgVectorSearchParameter parameter) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vector", parameter.getVector())
                .addValue("limit", parameter.getLimit())
                .addValue("objectType", parameter.getObjectType())
                .addValue("objectId", parameter.getObjectId());
        addMetadataParams(params, parameter);
        return params;
    }

    private static MapSqlParameterSource hybridSearchParams(PgVectorHybridSearchParameter parameter) {
        return searchParams(parameter)
                .addValue("query", parameter.getQuery())
                .addValue("vectorWeight", parameter.getVectorWeight())
                .addValue("lexicalWeight", parameter.getLexicalWeight());
    }

    private static void addMetadataParams(MapSqlParameterSource params, PgVectorSearchParameter parameter) {
        if (parameter.getMetadataObjectType() != null) {
            params.addValue("metadataObjectType", parameter.getMetadataObjectType());
        }
        if (parameter.getMetadataObjectId() != null) {
            params.addValue("metadataObjectId", parameter.getMetadataObjectId());
        }
        int index = 0;
        for (PgVectorMetadataEqualsCriterion criterion : parameter.getEqualsCriteria()) {
            params.addValue("metadataEqualsKey" + index, criterion.getKey());
            params.addValue("metadataEqualsValue" + index, criterion.getValue());
            index++;
        }
        index = 0;
        for (PgVectorMetadataInCriterion criterion : parameter.getInCriteria()) {
            if (criterion.getValues().isEmpty()) {
                continue;
            }
            params.addValue("metadataInKey" + index, criterion.getKey());
            params.addValue("metadataInValues" + index, criterion.getValues());
            index++;
        }
    }

    private static String filteredSql(String sql, PgVectorSearchParameter parameter) {
        List<String> conditions = metadataConditions(parameter);
        if (conditions.isEmpty()) {
            return sql;
        }
        int orderByIndex = sql.toLowerCase(java.util.Locale.ROOT).lastIndexOf("order by");
        String head = orderByIndex < 0 ? sql : sql.substring(0, orderByIndex);
        String tail = orderByIndex < 0 ? "" : sql.substring(orderByIndex);
        String conjunction = head.toLowerCase(java.util.Locale.ROOT).contains(" where ") ? " AND " : " WHERE ";
        return head + conjunction + String.join(" AND ", conditions) + tail;
    }

    private static List<String> metadataConditions(PgVectorSearchParameter parameter) {
        List<String> conditions = new ArrayList<>();
        if (parameter.getMetadataObjectType() != null) {
            conditions.add("object_type = :metadataObjectType");
        }
        if (parameter.getMetadataObjectId() != null) {
            conditions.add("object_id = :metadataObjectId");
        }
        int index = 0;
        for (PgVectorMetadataEqualsCriterion ignored : parameter.getEqualsCriteria()) {
            conditions.add("(metadata ->> :metadataEqualsKey" + index + ") = :metadataEqualsValue" + index);
            index++;
        }
        index = 0;
        for (PgVectorMetadataInCriterion criterion : parameter.getInCriteria()) {
            if (criterion.getValues().isEmpty()) {
                continue;
            }
            conditions.add("(metadata ->> :metadataInKey" + index + ") IN (:metadataInValues" + index + ")");
            index++;
        }
        return conditions;
    }
}
