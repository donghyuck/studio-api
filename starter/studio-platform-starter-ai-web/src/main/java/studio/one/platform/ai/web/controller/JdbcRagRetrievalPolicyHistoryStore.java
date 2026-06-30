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

import studio.one.platform.ai.web.dto.RagRetrievalPolicyHistoryDto;

public class JdbcRagRetrievalPolicyHistoryStore implements RagRetrievalPolicyHistoryStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<RagRetrievalPolicyHistoryDto> rowMapper = this::map;

    public JdbcRagRetrievalPolicyHistoryStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RagRetrievalPolicyHistoryDto save(RagRetrievalPolicyHistoryDto history) {
        RagRetrievalPolicyHistoryDto saved = history.createdAt() == null
                ? new RagRetrievalPolicyHistoryDto(
                        history.historyId(),
                        normalize(history.objectType()),
                        normalize(history.objectId()),
                        normalize(history.retrievalStrategy()),
                        normalize(history.reason()),
                        normalize(history.questionSetId()),
                        normalize(history.evaluationRunId()),
                        history.score(),
                        history.hitRate(),
                        history.mrr(),
                        history.averageElapsedMs(),
                        Instant.now())
                : history;
        jdbcTemplate.update("""
                INSERT INTO tb_ai_rag_retrieval_policy_history (
                    history_id, object_type, object_id, retrieval_strategy, reason,
                    question_set_id, evaluation_run_id, score, hit_rate, mrr,
                    average_elapsed_ms, created_at
                ) VALUES (
                    :historyId, :objectType, :objectId, :retrievalStrategy, :reason,
                    :questionSetId, :evaluationRunId, :score, :hitRate, :mrr,
                    :averageElapsedMs, :createdAt
                )
                """, params(saved));
        return saved;
    }

    @Override
    public List<RagRetrievalPolicyHistoryDto> list(String objectType, String objectId) {
        String sql = """
                SELECT history_id, object_type, object_id, retrieval_strategy, reason,
                       question_set_id, evaluation_run_id, score, hit_rate, mrr,
                       average_elapsed_ms, created_at
                FROM tb_ai_rag_retrieval_policy_history
                WHERE object_type = :objectType AND object_id = :objectId
                ORDER BY created_at DESC, history_id DESC
                LIMIT 100
                """;
        return jdbcTemplate.query(sql, Map.of(
                "objectType", normalize(objectType),
                "objectId", normalize(objectId)), rowMapper);
    }

    private MapSqlParameterSource params(RagRetrievalPolicyHistoryDto history) {
        return new MapSqlParameterSource()
                .addValue("historyId", history.historyId())
                .addValue("objectType", history.objectType())
                .addValue("objectId", history.objectId())
                .addValue("retrievalStrategy", history.retrievalStrategy())
                .addValue("reason", history.reason())
                .addValue("questionSetId", blankToNull(history.questionSetId()))
                .addValue("evaluationRunId", blankToNull(history.evaluationRunId()))
                .addValue("score", history.score())
                .addValue("hitRate", history.hitRate())
                .addValue("mrr", history.mrr())
                .addValue("averageElapsedMs", history.averageElapsedMs())
                .addValue("createdAt", timestamp(history.createdAt()));
    }

    private RagRetrievalPolicyHistoryDto map(ResultSet rs, int rowNum) throws SQLException {
        return new RagRetrievalPolicyHistoryDto(
                rs.getString("history_id"),
                rs.getString("object_type"),
                rs.getString("object_id"),
                rs.getString("retrieval_strategy"),
                rs.getString("reason"),
                rs.getString("question_set_id"),
                rs.getString("evaluation_run_id"),
                doubleValue(rs, "score"),
                doubleValue(rs, "hit_rate"),
                doubleValue(rs, "mrr"),
                doubleValue(rs, "average_elapsed_ms"),
                instant(rs.getTimestamp("created_at")));
    }

    private Double doubleValue(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
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
