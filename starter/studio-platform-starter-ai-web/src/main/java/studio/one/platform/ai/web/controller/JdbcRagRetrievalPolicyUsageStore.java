package studio.one.platform.ai.web.controller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageSummaryDto;

public class JdbcRagRetrievalPolicyUsageStore implements RagRetrievalPolicyUsageStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<RagRetrievalPolicyUsageDto> rowMapper = this::map;

    public JdbcRagRetrievalPolicyUsageStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RagRetrievalPolicyUsageDto save(RagRetrievalPolicyUsageDto usage) {
        RagRetrievalPolicyUsageDto saved = usage.createdAt() == null
                ? new RagRetrievalPolicyUsageDto(
                        usage.usageId(),
                        normalize(usage.objectType()),
                        normalize(usage.objectId()),
                        normalize(usage.retrievalStrategy()),
                        normalize(usage.questionSetId()),
                        normalize(usage.evaluationRunId()),
                        usage.topK(),
                        usage.minScore(),
                        usage.resultCount(),
                        usage.skippedChat(),
                        usage.elapsedMs(),
                        Instant.now())
                : usage;
        jdbcTemplate.update("""
                INSERT INTO tb_ai_rag_retrieval_policy_usage (
                    usage_id, object_type, object_id, retrieval_strategy,
                    question_set_id, evaluation_run_id, top_k, min_score,
                    result_count, skipped_chat, elapsed_ms, created_at
                ) VALUES (
                    :usageId, :objectType, :objectId, :retrievalStrategy,
                    :questionSetId, :evaluationRunId, :topK, :minScore,
                    :resultCount, :skippedChat, :elapsedMs, :createdAt
                )
                """, params(saved));
        return saved;
    }

    @Override
    public List<RagRetrievalPolicyUsageDto> list(String objectType, String objectId) {
        String sql = """
                SELECT usage_id, object_type, object_id, retrieval_strategy,
                       question_set_id, evaluation_run_id, top_k, min_score,
                       result_count, skipped_chat, elapsed_ms, created_at
                FROM tb_ai_rag_retrieval_policy_usage
                WHERE object_type = :objectType AND object_id = :objectId
                ORDER BY created_at DESC, usage_id DESC
                LIMIT 100
                """;
        return jdbcTemplate.query(sql, Map.of(
                "objectType", normalize(objectType),
                "objectId", normalize(objectId)), rowMapper);
    }

    @Override
    public RagRetrievalPolicyUsageSummaryDto summary(String objectType, String objectId) {
        String sql = """
                SELECT COUNT(*) AS usage_count,
                       AVG(result_count) AS average_result_count,
                       AVG(elapsed_ms) AS average_elapsed_ms,
                       SUM(CASE WHEN skipped_chat THEN 1 ELSE 0 END) AS skipped_chat_count
                FROM tb_ai_rag_retrieval_policy_usage
                WHERE object_type = :objectType AND object_id = :objectId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("objectType", normalize(objectType))
                .addValue("objectId", normalize(objectId));
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> {
            int usageCount = rs.getInt("usage_count");
            List<RagRetrievalPolicyUsageDto> latest = usageCount == 0 ? List.of() : list(objectType, objectId);
            return new RagRetrievalPolicyUsageSummaryDto(
                    normalize(objectType),
                    normalize(objectId),
                    usageCount,
                    doubleValue(rs, "average_result_count", 0.0d),
                    doubleValue(rs, "average_elapsed_ms", 0.0d),
                    rs.getInt("skipped_chat_count"),
                    latest.isEmpty() ? null : latest.get(0).retrievalStrategy());
        });
    }

    private MapSqlParameterSource params(RagRetrievalPolicyUsageDto usage) {
        return new MapSqlParameterSource()
                .addValue("usageId", usage.usageId())
                .addValue("objectType", usage.objectType())
                .addValue("objectId", usage.objectId())
                .addValue("retrievalStrategy", usage.retrievalStrategy())
                .addValue("questionSetId", blankToNull(usage.questionSetId()))
                .addValue("evaluationRunId", blankToNull(usage.evaluationRunId()))
                .addValue("topK", usage.topK())
                .addValue("minScore", usage.minScore())
                .addValue("resultCount", usage.resultCount())
                .addValue("skippedChat", usage.skippedChat())
                .addValue("elapsedMs", usage.elapsedMs())
                .addValue("createdAt", timestamp(usage.createdAt()));
    }

    private RagRetrievalPolicyUsageDto map(ResultSet rs, int rowNum) throws SQLException {
        return new RagRetrievalPolicyUsageDto(
                rs.getString("usage_id"),
                rs.getString("object_type"),
                rs.getString("object_id"),
                rs.getString("retrieval_strategy"),
                rs.getString("question_set_id"),
                rs.getString("evaluation_run_id"),
                intValue(rs, "top_k"),
                doubleValue(rs, "min_score", null),
                rs.getInt("result_count"),
                rs.getBoolean("skipped_chat"),
                rs.getLong("elapsed_ms"),
                instant(rs.getTimestamp("created_at")));
    }

    private Integer intValue(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double doubleValue(ResultSet rs, String column, Double fallback) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? fallback : value;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
