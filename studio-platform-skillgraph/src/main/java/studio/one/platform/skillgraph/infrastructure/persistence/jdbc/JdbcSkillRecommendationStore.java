package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationJob;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResult;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResultStatus;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationTargetHit;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationType;
import studio.one.platform.skillgraph.domain.port.SkillRecommendationStore;

@RequiredArgsConstructor
public class JdbcSkillRecommendationStore implements SkillRecommendationStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public SkillRecommendationJob saveJob(SkillRecommendationJob job) {
        int updated = template.update("""
                UPDATE tb_skill_recommendation_job
                SET target_scope = :targetScope,
                    target_filter = CAST(:targetFilter AS jsonb),
                    embedding_provider = :embeddingProvider,
                    embedding_model = :embeddingModel,
                    embedding_dimension = :embeddingDimension,
                    target_types = :targetTypes,
                    top_k = :topK,
                    min_score = :minScore,
                    new_skill_min_confidence = :newSkillMinConfidence,
                    existing_skill_min_score = :existingSkillMinScore,
                    status = :status,
                    total_count = :totalCount,
                    processed_count = :processedCount,
                    result_count = :resultCount,
                    failed_count = :failedCount,
                    error_message = :errorMessage,
                    started_at = :startedAt,
                    completed_at = :completedAt,
                    updated_at = :updatedAt
                WHERE job_id = :jobId
                """, jobParams(job));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_recommendation_job (
                        job_id, target_scope, target_filter, embedding_provider, embedding_model,
                        embedding_dimension, target_types, top_k, min_score, new_skill_min_confidence,
                        existing_skill_min_score, status, total_count, processed_count, result_count,
                        failed_count, error_message, created_at, started_at, completed_at, updated_at)
                    VALUES (
                        :jobId, :targetScope, CAST(:targetFilter AS jsonb), :embeddingProvider, :embeddingModel,
                        :embeddingDimension, :targetTypes, :topK, :minScore, :newSkillMinConfidence,
                        :existingSkillMinScore, :status, :totalCount, :processedCount, :resultCount,
                        :failedCount, :errorMessage, :createdAt, :startedAt, :completedAt, :updatedAt)
                    """, jobParams(job));
        }
        return job;
    }

    @Override
    public Optional<SkillRecommendationJob> findJob(String jobId) {
        return template.query("""
                SELECT * FROM tb_skill_recommendation_job
                WHERE job_id = :jobId
                """, Map.of("jobId", jobId), this::mapJob).stream().findFirst();
    }

    @Override
    public Page<SkillRecommendationJob> searchJobs(Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<SkillRecommendationJob> content = template.query("""
                SELECT * FROM tb_skill_recommendation_job
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """, params, this::mapJob);
        Long total = template.queryForObject("SELECT COUNT(*) FROM tb_skill_recommendation_job", Map.of(), Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public SkillRecommendationResult saveResult(SkillRecommendationResult result) {
        template.update("""
                INSERT INTO tb_skill_recommendation_result (
                    result_id, job_id, source_type, source_id, source_text, target_source_type,
                    target_source_id, target_text, recommendation_type, similarity_score, confidence,
                    score_detail, reason, status, apply_type, applied_at, applied_by, created_at)
                VALUES (
                    :resultId, :jobId, :sourceType, :sourceId, :sourceText, :targetSourceType,
                    :targetSourceId, :targetText, :recommendationType, :similarityScore, :confidence,
                    CAST(:scoreDetail AS jsonb), CAST(:reason AS jsonb), :status, :applyType, :appliedAt, :appliedBy, :createdAt)
                """, resultParams(result));
        return result;
    }

    @Override
    public Optional<SkillRecommendationResult> findResult(String resultId) {
        return template.query("""
                SELECT * FROM tb_skill_recommendation_result
                WHERE result_id = :resultId
                """, Map.of("resultId", resultId), this::mapResult).stream().findFirst();
    }

    @Override
    public Page<SkillRecommendationResult> findResultsByJob(String jobId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<SkillRecommendationResult> content = template.query("""
                SELECT * FROM tb_skill_recommendation_result
                WHERE job_id = :jobId
                ORDER BY created_at, similarity_score DESC
                LIMIT :limit OFFSET :offset
                """, params, this::mapResult);
        Long total = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_recommendation_result
                WHERE job_id = :jobId
                """, params, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SkillRecommendationResult> findResultsByCandidate(String candidateId) {
        return template.query("""
                SELECT * FROM tb_skill_recommendation_result
                WHERE source_type = 'SKILL_CANDIDATE' AND source_id = :candidateId
                ORDER BY created_at DESC, similarity_score DESC
                """, Map.of("candidateId", candidateId), this::mapResult);
    }

    @Override
    public int updateResultStatus(
            String resultId,
            SkillRecommendationResultStatus status,
            String applyType,
            String appliedBy,
            String reason) {
        return template.update("""
                UPDATE tb_skill_recommendation_result
                SET status = :status,
                    apply_type = COALESCE(:applyType, apply_type),
                    applied_at = CASE WHEN :status = 'APPLIED' THEN :now ELSE applied_at END,
                    applied_by = COALESCE(:appliedBy, applied_by),
                    reason = COALESCE(CAST(:reason AS jsonb), reason)
                WHERE result_id = :resultId
                """, new MapSqlParameterSource()
                .addValue("resultId", resultId)
                .addValue("status", status.name())
                .addValue("applyType", applyType)
                .addValue("appliedBy", appliedBy)
                .addValue("reason", reason == null ? null : jsonReason(reason))
                .addValue("now", Timestamp.from(Instant.now())));
    }

    @Override
    public List<Double> findEmbedding(String sourceType, String sourceId, String provider, String model, int dimension) {
        return template.query("""
                SELECT embedding::text AS embedding_text
                FROM tb_skill_embedding
                WHERE source_type = :sourceType
                  AND source_id = :sourceId
                  AND embedding_provider = :provider
                  AND embedding_model = :model
                  AND embedding_dimension = :dimension
                  AND embedding IS NOT NULL
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId)
                .addValue("provider", provider)
                .addValue("model", model)
                .addValue("dimension", dimension), (rs, rowNum) -> parseVector(rs.getString("embedding_text")))
                .stream()
                .findFirst()
                .orElse(List.of());
    }

    @Override
    public List<SkillRecommendationTargetHit> findNearestEmbeddings(
            String sourceType,
            String provider,
            String model,
            int dimension,
            List<Double> embedding,
            String excludedSourceId,
            int limit,
            double minScore) {
        if ("DATASET_CONCEPT".equals(sourceType)) {
            return findNearestDatasetConcepts(provider, model, dimension, embedding, limit, minScore);
        }
        String vector = embedding.toString();
        return template.getJdbcOperations().query(con -> {
            var ps = con.prepareStatement("""
                    SELECT e.source_type,
                           e.source_id,
                           e.embedding_text,
                           1 - (e.embedding <=> CAST(? AS vector)) AS score
                    FROM tb_skill_embedding e
                    WHERE e.source_type = ?
                      AND e.embedding_provider = ?
                      AND e.embedding_model = ?
                      AND e.embedding_dimension = ?
                      AND e.embedding IS NOT NULL
                      AND (? IS NULL OR e.source_id <> ?)
                      AND 1 - (e.embedding <=> CAST(? AS vector)) >= ?
                    ORDER BY e.embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """);
            ps.setString(1, vector);
            ps.setString(2, sourceType);
            ps.setString(3, provider);
            ps.setString(4, model);
            ps.setInt(5, dimension);
            ps.setString(6, excludedSourceId);
            ps.setString(7, excludedSourceId);
            ps.setString(8, vector);
            ps.setDouble(9, minScore);
            ps.setString(10, vector);
            ps.setInt(11, limit <= 0 ? 5 : limit);
            return ps;
        }, (rs, rowNum) -> new SkillRecommendationTargetHit(
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("embedding_text"),
                rs.getDouble("score")));
    }

    private List<SkillRecommendationTargetHit> findNearestDatasetConcepts(
            String provider,
            String model,
            int dimension,
            List<Double> embedding,
            int limit,
            double minScore) {
        String vector = embedding.toString();
        return template.getJdbcOperations().query(con -> {
            var ps = con.prepareStatement("""
                    SELECT 'DATASET_CONCEPT' AS source_type,
                           e.concept_id AS source_id,
                           e.source_text AS embedding_text,
                           1 - (e.embedding <=> CAST(? AS vector)) AS score
                    FROM tb_skill_dataset_concept_embedding e
                    WHERE e.embedding_provider = ?
                      AND e.embedding_model = ?
                      AND e.embedding_dim = ?
                      AND e.embedding IS NOT NULL
                      AND 1 - (e.embedding <=> CAST(? AS vector)) >= ?
                    ORDER BY e.embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """);
            ps.setString(1, vector);
            ps.setString(2, provider);
            ps.setString(3, model);
            ps.setInt(4, dimension);
            ps.setString(5, vector);
            ps.setDouble(6, minScore);
            ps.setString(7, vector);
            ps.setInt(8, limit <= 0 ? 5 : limit);
            return ps;
        }, (rs, rowNum) -> new SkillRecommendationTargetHit(
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("embedding_text"),
                rs.getDouble("score")));
    }

    private MapSqlParameterSource jobParams(SkillRecommendationJob job) {
        return new MapSqlParameterSource()
                .addValue("jobId", job.jobId())
                .addValue("targetScope", job.targetScope())
                .addValue("targetFilter", job.targetFilter() == null ? "{}" : job.targetFilter())
                .addValue("embeddingProvider", job.embeddingProvider())
                .addValue("embeddingModel", job.embeddingModel())
                .addValue("embeddingDimension", job.embeddingDimension())
                .addValue("targetTypes", job.targetTypes())
                .addValue("topK", job.topK())
                .addValue("minScore", job.minScore())
                .addValue("newSkillMinConfidence", job.newSkillMinConfidence())
                .addValue("existingSkillMinScore", job.existingSkillMinScore())
                .addValue("status", job.status().name())
                .addValue("totalCount", job.totalCount())
                .addValue("processedCount", job.processedCount())
                .addValue("resultCount", job.resultCount())
                .addValue("failedCount", job.failedCount())
                .addValue("errorMessage", job.errorMessage())
                .addValue("createdAt", timestamp(job.createdAt()))
                .addValue("startedAt", timestamp(job.startedAt()))
                .addValue("completedAt", timestamp(job.completedAt()))
                .addValue("updatedAt", timestamp(job.updatedAt()));
    }

    private MapSqlParameterSource resultParams(SkillRecommendationResult result) {
        return new MapSqlParameterSource()
                .addValue("resultId", result.resultId())
                .addValue("jobId", result.jobId())
                .addValue("sourceType", result.sourceType())
                .addValue("sourceId", result.sourceId())
                .addValue("sourceText", result.sourceText())
                .addValue("targetSourceType", result.targetSourceType())
                .addValue("targetSourceId", result.targetSourceId())
                .addValue("targetText", result.targetText())
                .addValue("recommendationType", result.recommendationType().name())
                .addValue("similarityScore", result.similarityScore())
                .addValue("confidence", result.confidence())
                .addValue("scoreDetail", result.scoreDetail() == null ? "{}" : result.scoreDetail())
                .addValue("reason", jsonReason(result.reason()))
                .addValue("status", result.status().name())
                .addValue("applyType", result.applyType())
                .addValue("appliedAt", timestamp(result.appliedAt()))
                .addValue("appliedBy", result.appliedBy())
                .addValue("createdAt", timestamp(result.createdAt()));
    }

    private SkillRecommendationJob mapJob(ResultSet rs, int rowNum) throws SQLException {
        return new SkillRecommendationJob(
                rs.getString("job_id"),
                rs.getString("target_scope"),
                rs.getString("target_filter"),
                rs.getString("embedding_provider"),
                rs.getString("embedding_model"),
                rs.getInt("embedding_dimension"),
                rs.getString("target_types"),
                rs.getInt("top_k"),
                rs.getDouble("min_score"),
                rs.getDouble("new_skill_min_confidence"),
                rs.getDouble("existing_skill_min_score"),
                SkillRecommendationJobStatus.valueOf(rs.getString("status")),
                rs.getLong("total_count"),
                rs.getLong("processed_count"),
                rs.getLong("result_count"),
                rs.getLong("failed_count"),
                rs.getString("error_message"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private SkillRecommendationResult mapResult(ResultSet rs, int rowNum) throws SQLException {
        return new SkillRecommendationResult(
                rs.getString("result_id"),
                rs.getString("job_id"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("source_text"),
                rs.getString("target_source_type"),
                rs.getString("target_source_id"),
                rs.getString("target_text"),
                SkillRecommendationType.valueOf(rs.getString("recommendation_type")),
                rs.getDouble("similarity_score"),
                rs.getDouble("confidence"),
                rs.getString("score_detail"),
                rs.getString("reason"),
                SkillRecommendationResultStatus.valueOf(rs.getString("status")),
                rs.getString("apply_type"),
                instant(rs.getTimestamp("applied_at")),
                rs.getString("applied_by"),
                instant(rs.getTimestamp("created_at")));
    }

    private List<Double> parseVector(String value) {
        if (value == null || value.length() < 2) {
            return List.of();
        }
        String[] parts = value.substring(1, value.length() - 1).split(",");
        List<Double> vector = new ArrayList<>(parts.length);
        for (String part : parts) {
            vector.add(Double.parseDouble(part.trim()));
        }
        return vector;
    }

    private String jsonReason(String reason) {
        return "{\"message\":\"" + (reason == null ? "" : reason.replace("\"", "\\\"")) + "\"}";
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
