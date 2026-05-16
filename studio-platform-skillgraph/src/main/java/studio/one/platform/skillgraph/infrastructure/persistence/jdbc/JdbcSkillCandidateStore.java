package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
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
    public List<SkillCandidate> searchCandidates(SkillCandidateStatus status, String q, int limit) {
        String query = q == null ? "" : q.trim().toLowerCase();
        int max = limit <= 0 ? 100 : limit;
        return template.query("""
                SELECT * FROM tb_skill_candidate
                WHERE (:status IS NULL OR status = :status)
                  AND (:q = '' OR LOWER(term) LIKE :likeQ OR normalized_term LIKE :likeQ)
                ORDER BY created_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("status", status == null ? null : status.name())
                .addValue("q", query)
                .addValue("likeQ", "%" + query + "%")
                .addValue("limit", max), this::mapCandidate);
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
}
