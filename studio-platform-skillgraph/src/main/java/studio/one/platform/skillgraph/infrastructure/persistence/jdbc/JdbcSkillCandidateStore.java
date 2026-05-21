package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStats;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;

@RequiredArgsConstructor
public class JdbcSkillCandidateStore implements SkillCandidateStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public SkillSourceChunk saveSourceChunk(SkillSourceChunk chunk) {
        template.update("""
                INSERT INTO tb_skill_source_chunk
                    (source_chunk_id, source_type, source_id, chunk_id, text, created_at)
                VALUES
                    (:sourceChunkId, :sourceType, :sourceId, :chunkId, :text, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("sourceChunkId", chunk.sourceChunkId())
                .addValue("sourceType", chunk.sourceType())
                .addValue("sourceId", chunk.sourceId())
                .addValue("chunkId", chunk.chunkId())
                .addValue("text", chunk.text())
                .addValue("createdAt", Timestamp.from(chunk.createdAt())));
        return chunk;
    }

    @Override
    public SkillCandidate saveCandidate(SkillCandidate candidate) {
        int updated = template.update("""
                UPDATE tb_skill_candidate
                SET status = :status,
                    confidence = :confidence,
                    occurrence_count = :occurrenceCount,
                    matched_skill_id = :matchedSkillId,
                    reviewer_note = :reviewerNote,
                    updated_at = :updatedAt
                WHERE candidate_id = :candidateId
                """, candidateParams(candidate));
        if (updated > 0) {
            return candidate;
        }
        template.update("""
                INSERT INTO tb_skill_candidate
                    (candidate_id, source_chunk_id, source_type, source_id, term, normalized_term,
                     status, confidence, occurrence_count, matched_skill_id, reviewer_note, created_at, updated_at)
                VALUES
                    (:candidateId, :sourceChunkId, :sourceType, :sourceId, :term, :normalizedTerm,
                     :status, :confidence, :occurrenceCount, :matchedSkillId, :reviewerNote, :createdAt, :updatedAt)
                """, candidateParams(candidate));
        return candidate;
    }

    @Override
    public Optional<SkillCandidate> findCandidate(String candidateId) {
        return queryOne("""
                SELECT * FROM tb_skill_candidate WHERE candidate_id = :candidateId
                """, Map.of("candidateId", candidateId));
    }

    @Override
    public Optional<SkillCandidate> findCandidateByNormalizedTerm(String normalizedTerm) {
        return queryOne("""
                SELECT * FROM tb_skill_candidate
                WHERE normalized_term = :normalizedTerm
                ORDER BY created_at DESC
                LIMIT 1
                """, Map.of("normalizedTerm", normalizedTerm));
    }

    @Override
    public List<SkillCandidateStats> findCandidateStatsBySkillIds(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return template.query("""
                SELECT matched_skill_id AS skill_id,
                       COALESCE(SUM(occurrence_count), 0) AS occurrence_count,
                       COALESCE(MAX(confidence), 0) AS confidence_score
                FROM tb_skill_candidate
                WHERE matched_skill_id IN (:skillIds)
                GROUP BY matched_skill_id
                """, Map.of("skillIds", skillIds), (rs, rowNum) -> new SkillCandidateStats(
                rs.getString("skill_id"),
                rs.getInt("occurrence_count"),
                rs.getDouble("confidence_score")));
    }

    @Override
    public Optional<SkillCandidate> findCandidateBySourceAndNormalizedTerm(
            String sourceType,
            String sourceId,
            String chunkId,
            String normalizedTerm) {
        List<SkillCandidate> results = template.query("""
                SELECT * FROM tb_skill_candidate
                WHERE normalized_term = :normalizedTerm
                  AND COALESCE(source_type, '') = COALESCE(:sourceType, '')
                  AND COALESCE(source_id, '') = COALESCE(:sourceId, '')
                  AND COALESCE((SELECT chunk_id FROM tb_skill_source_chunk sc
                                WHERE sc.source_chunk_id = tb_skill_candidate.source_chunk_id), '') = COALESCE(:chunkId, '')
                ORDER BY created_at DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("normalizedTerm", normalizedTerm)
                .addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId)
                .addValue("chunkId", chunkId), this::mapCandidate);
        return results.stream().findFirst();
    }

    @Override
    public Page<SkillCandidate> searchCandidates(
            SkillCandidateStatus status,
            String q,
            String sourceType,
            String sourceId,
            Pageable pageable) {
        String query = q == null ? "" : q.trim().toLowerCase();
        int limit = pageable.getPageSize();
        long offset = pageable.getOffset();
        StringBuilder sql = new StringBuilder("""
                FROM tb_skill_candidate
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (status != null) {
            sql.append("  AND status = :status\n");
            params.addValue("status", status.name());
        }
        if (!query.isBlank()) {
            sql.append("  AND (LOWER(term) LIKE :likeQ OR normalized_term LIKE :likeQ)\n");
            params.addValue("likeQ", "%" + query + "%");
        }
        String normalizedSourceType = normalize(sourceType);
        if (normalizedSourceType != null) {
            sql.append("  AND source_type = :sourceType\n");
            params.addValue("sourceType", normalizedSourceType);
        }
        String normalizedSourceId = normalize(sourceId);
        if (normalizedSourceId != null) {
            sql.append("  AND source_id = :sourceId\n");
            params.addValue("sourceId", normalizedSourceId);
        }
        params.addValue("limit", limit)
                .addValue("offset", offset);
        List<SkillCandidate> content = template.query("""
                SELECT *
                """ + sql + """
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """, params, this::mapCandidate);
        Long total = template.queryForObject("""
                SELECT COUNT(*)
                """ + sql,
                params,
                Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private Optional<SkillCandidate> queryOne(String sql, Map<String, ?> params) {
        List<SkillCandidate> results = template.query(sql, params, this::mapCandidate);
        return results.stream().findFirst();
    }

    private MapSqlParameterSource candidateParams(SkillCandidate candidate) {
        return new MapSqlParameterSource()
                .addValue("candidateId", candidate.candidateId())
                .addValue("sourceChunkId", candidate.sourceChunkId())
                .addValue("sourceType", candidate.sourceType())
                .addValue("sourceId", candidate.sourceId())
                .addValue("term", candidate.term())
                .addValue("normalizedTerm", candidate.normalizedTerm())
                .addValue("status", candidate.status().name())
                .addValue("confidence", candidate.confidence())
                .addValue("occurrenceCount", candidate.occurrenceCount())
                .addValue("matchedSkillId", candidate.matchedSkillId())
                .addValue("reviewerNote", candidate.reviewerNote())
                .addValue("createdAt", Timestamp.from(candidate.createdAt()))
                .addValue("updatedAt", Timestamp.from(candidate.updatedAt()));
    }

    private SkillCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new SkillCandidate(
                rs.getString("candidate_id"),
                rs.getString("source_chunk_id"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("term"),
                rs.getString("normalized_term"),
                SkillCandidateStatus.valueOf(rs.getString("status")),
                rs.getDouble("confidence"),
                rs.getInt("occurrence_count"),
                rs.getString("matched_skill_id"),
                rs.getString("reviewer_note"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
