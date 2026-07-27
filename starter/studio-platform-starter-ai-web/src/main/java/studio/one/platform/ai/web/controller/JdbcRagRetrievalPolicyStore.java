package studio.one.platform.ai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyDto;

public class JdbcRagRetrievalPolicyStore implements RagRetrievalPolicyStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<RagRetrievalPolicyDto> rowMapper = this::map;

    public JdbcRagRetrievalPolicyStore(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagRetrievalPolicyDto save(RagRetrievalPolicyDto policy) {
        Optional<RagRetrievalPolicyDto> existing = find(policy.objectType(), policy.objectId());
        Instant now = Instant.now();
        RagRetrievalPolicyDto saved = new RagRetrievalPolicyDto(
                normalize(policy.objectType()),
                normalize(policy.objectId()),
                normalize(policy.retrievalStrategy()),
                policy.retrievalOptions(),
                normalize(policy.questionSetId()),
                normalize(policy.evaluationRunId()),
                policy.score(),
                policy.hitRate(),
                policy.mrr(),
                policy.averageElapsedMs(),
                existing.map(RagRetrievalPolicyDto::createdAt).orElse(now),
                now);
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE tb_ai_rag_retrieval_policy
                    SET retrieval_strategy = :retrievalStrategy,
                        retrieval_options_json = :retrievalOptionsJson,
                        question_set_id = :questionSetId,
                        evaluation_run_id = :evaluationRunId,
                        score = :score,
                        hit_rate = :hitRate,
                        mrr = :mrr,
                        average_elapsed_ms = :averageElapsedMs,
                        updated_at = :updatedAt
                    WHERE object_type = :objectType AND object_id = :objectId
                    """, params(saved));
        } else {
            jdbcTemplate.update("""
                    INSERT INTO tb_ai_rag_retrieval_policy (
                        object_type, object_id, retrieval_strategy, retrieval_options_json,
                        question_set_id, evaluation_run_id, score, hit_rate, mrr,
                        average_elapsed_ms, created_at, updated_at
                    ) VALUES (
                        :objectType, :objectId, :retrievalStrategy, :retrievalOptionsJson,
                        :questionSetId, :evaluationRunId, :score, :hitRate, :mrr,
                        :averageElapsedMs, :createdAt, :updatedAt
                    )
                    """, params(saved));
        }
        return saved;
    }

    @Override
    public Optional<RagRetrievalPolicyDto> find(String objectType, String objectId) {
        String sql = """
                SELECT object_type, object_id, retrieval_strategy, retrieval_options_json,
                       question_set_id, evaluation_run_id, score, hit_rate, mrr,
                       average_elapsed_ms, created_at, updated_at
                FROM tb_ai_rag_retrieval_policy
                WHERE object_type = :objectType AND object_id = :objectId
                """;
        List<RagRetrievalPolicyDto> rows = jdbcTemplate.query(
                sql,
                Map.of("objectType", normalize(objectType), "objectId", normalize(objectId)),
                rowMapper);
        return rows.stream().findFirst();
    }

    @Override
    public List<RagRetrievalPolicyDto> list() {
        String sql = """
                SELECT object_type, object_id, retrieval_strategy, retrieval_options_json,
                       question_set_id, evaluation_run_id, score, hit_rate, mrr,
                       average_elapsed_ms, created_at, updated_at
                FROM tb_ai_rag_retrieval_policy
                ORDER BY updated_at DESC, object_type, object_id
                LIMIT 200
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    private MapSqlParameterSource params(RagRetrievalPolicyDto policy) {
        return new MapSqlParameterSource()
                .addValue("objectType", policy.objectType())
                .addValue("objectId", policy.objectId())
                .addValue("retrievalStrategy", policy.retrievalStrategy())
                .addValue("retrievalOptionsJson", writeOptions(policy.retrievalOptions()))
                .addValue("questionSetId", blankToNull(policy.questionSetId()))
                .addValue("evaluationRunId", blankToNull(policy.evaluationRunId()))
                .addValue("score", policy.score())
                .addValue("hitRate", policy.hitRate())
                .addValue("mrr", policy.mrr())
                .addValue("averageElapsedMs", policy.averageElapsedMs())
                .addValue("createdAt", timestamp(policy.createdAt()))
                .addValue("updatedAt", timestamp(policy.updatedAt()));
    }

    private RagRetrievalPolicyDto map(ResultSet rs, int rowNum) throws SQLException {
        return new RagRetrievalPolicyDto(
                rs.getString("object_type"),
                rs.getString("object_id"),
                rs.getString("retrieval_strategy"),
                readOptions(rs.getString("retrieval_options_json")),
                rs.getString("question_set_id"),
                rs.getString("evaluation_run_id"),
                doubleValue(rs, "score"),
                doubleValue(rs, "hit_rate"),
                doubleValue(rs, "mrr"),
                doubleValue(rs, "average_elapsed_ms"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private String writeOptions(ChatRagRetrievalOptionsDto options) {
        if (options == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid RAG retrieval policy options JSON", ex);
        }
    }

    private ChatRagRetrievalOptionsDto readOptions(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ChatRagRetrievalOptionsDto.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid RAG retrieval policy options JSON", ex);
        }
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
