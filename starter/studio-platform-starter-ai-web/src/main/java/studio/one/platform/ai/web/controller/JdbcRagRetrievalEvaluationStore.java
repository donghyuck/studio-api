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

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

public class JdbcRagRetrievalEvaluationStore implements RagRetrievalEvaluationStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<RagRetrievalEvaluationResponseDto> rowMapper = this::map;

    public JdbcRagRetrievalEvaluationStore(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagRetrievalEvaluationResponseDto save(RagRetrievalEvaluationResponseDto result) {
        String sql = """
                INSERT INTO tb_ai_rag_retrieval_evaluation (
                    run_id, created_at, object_type, object_id, embedding_profile_id,
                    embedding_provider, embedding_model, top_k, min_score, question_set_id, result_json
                ) VALUES (
                    :runId, :createdAt, :objectType, :objectId, :embeddingProfileId,
                    :embeddingProvider, :embeddingModel, :topK, :minScore, :questionSetId, :resultJson
                )
                """;
        jdbcTemplate.update(sql, params(result));
        return result;
    }

    @Override
    public Optional<RagRetrievalEvaluationResponseDto> find(String runId) {
        String sql = """
                SELECT result_json
                FROM tb_ai_rag_retrieval_evaluation
                WHERE run_id = :runId
                """;
        List<RagRetrievalEvaluationResponseDto> rows = jdbcTemplate.query(
                sql,
                Map.of("runId", runId),
                rowMapper);
        return rows.stream().findFirst();
    }

    @Override
    public List<RagRetrievalEvaluationResponseDto> list() {
        String sql = """
                SELECT result_json
                FROM tb_ai_rag_retrieval_evaluation
                ORDER BY created_at DESC, run_id DESC
                LIMIT 100
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<RagRetrievalEvaluationResponseDto> listByQuestionSet(String questionSetId) {
        String sql = """
                SELECT result_json
                FROM tb_ai_rag_retrieval_evaluation
                WHERE question_set_id = :questionSetId
                ORDER BY created_at DESC, run_id DESC
                LIMIT 100
                """;
        return jdbcTemplate.query(sql, Map.of("questionSetId", questionSetId), rowMapper);
    }

    private MapSqlParameterSource params(RagRetrievalEvaluationResponseDto result) {
        return new MapSqlParameterSource()
                .addValue("runId", result.runId())
                .addValue("createdAt", timestamp(result.createdAt()))
                .addValue("objectType", result.objectType())
                .addValue("objectId", result.objectId())
                .addValue("questionSetId", result.questionSetId())
                .addValue("embeddingProfileId", result.embeddingProfileId())
                .addValue("embeddingProvider", result.embeddingProvider())
                .addValue("embeddingModel", result.embeddingModel())
                .addValue("topK", result.topK())
                .addValue("minScore", result.minScore())
                .addValue("resultJson", writeJson(result));
    }

    private RagRetrievalEvaluationResponseDto map(ResultSet rs, int rowNum) throws SQLException {
        return readJson(rs.getString("result_json"));
    }

    private String writeJson(RagRetrievalEvaluationResponseDto result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid retrieval evaluation result JSON", ex);
        }
    }

    private RagRetrievalEvaluationResponseDto readJson(String json) {
        try {
            return objectMapper.readValue(json, RagRetrievalEvaluationResponseDto.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid retrieval evaluation result JSON", ex);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
