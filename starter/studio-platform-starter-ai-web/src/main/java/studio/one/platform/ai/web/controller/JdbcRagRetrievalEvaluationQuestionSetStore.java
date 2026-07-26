package studio.one.platform.ai.web.controller;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRequestDto;

public class JdbcRagRetrievalEvaluationQuestionSetStore implements RagRetrievalEvaluationQuestionSetStore {
    private static final TypeReference<List<RagRetrievalEvaluationRequestDto.Question>> QUESTION_LIST_TYPE =
            new TypeReference<>() {
            };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<RagRetrievalEvaluationQuestionSetDto> rowMapper = this::map;

    public JdbcRagRetrievalEvaluationQuestionSetStore(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagRetrievalEvaluationQuestionSetDto save(RagRetrievalEvaluationQuestionSetDto questionSet) {
        jdbcTemplate.update("DELETE FROM tb_ai_rag_retrieval_evaluation_question_set WHERE question_set_id = :id",
                Map.of("id", questionSet.questionSetId()));
        String sql = """
                INSERT INTO tb_ai_rag_retrieval_evaluation_question_set (
                    question_set_id, name, description, created_at, updated_at, questions_json
                ) VALUES (
                    :questionSetId, :name, :description, :createdAt, :updatedAt, :questionsJson
                )
                """;
        jdbcTemplate.update(sql, params(questionSet));
        return questionSet;
    }

    @Override
    public Optional<RagRetrievalEvaluationQuestionSetDto> find(String questionSetId) {
        String sql = """
                SELECT *
                FROM tb_ai_rag_retrieval_evaluation_question_set
                WHERE question_set_id = :questionSetId
                """;
        return jdbcTemplate.query(sql, Map.of("questionSetId", questionSetId), rowMapper).stream().findFirst();
    }

    @Override
    public List<RagRetrievalEvaluationQuestionSetDto> list() {
        String sql = """
                SELECT *
                FROM tb_ai_rag_retrieval_evaluation_question_set
                ORDER BY updated_at DESC, question_set_id DESC
                LIMIT 100
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    private MapSqlParameterSource params(RagRetrievalEvaluationQuestionSetDto questionSet) {
        return new MapSqlParameterSource()
                .addValue("questionSetId", questionSet.questionSetId())
                .addValue("name", questionSet.name())
                .addValue("description", questionSet.description())
                .addValue("createdAt", timestamp(questionSet.createdAt()))
                .addValue("updatedAt", timestamp(questionSet.updatedAt()))
                .addValue("questionsJson", writeQuestions(questionSet.questions()));
    }

    private RagRetrievalEvaluationQuestionSetDto map(ResultSet rs, int rowNum) throws SQLException {
        return new RagRetrievalEvaluationQuestionSetDto(
                rs.getString("question_set_id"),
                rs.getString("name"),
                rs.getString("description"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                readQuestions(rs.getString("questions_json")));
    }

    private String writeQuestions(List<RagRetrievalEvaluationRequestDto.Question> questions) {
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid retrieval evaluation question set JSON", ex);
        }
    }

    private List<RagRetrievalEvaluationRequestDto.Question> readQuestions(String json) {
        try {
            return objectMapper.readValue(json, QUESTION_LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid retrieval evaluation question set JSON", ex);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
