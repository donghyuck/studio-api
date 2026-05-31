package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStats;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillEmbeddingMetadata;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;

@RequiredArgsConstructor
public class JdbcSkillCandidateStore implements SkillCandidateStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

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
                    search_text = :searchText,
                    skill_type = :skillType,
                    action = :action,
                    technology = :technology,
                    target = :target,
                    evidence_text = :evidenceText,
                    context = :context,
                    difficulty = :difficulty,
                    extraction_method = :extractionMethod,
                    confidence_detail = :confidenceDetail,
                    source_position = :sourcePosition,
                    normalization_info = :normalizationInfo,
                    mapping_candidates = :mappingCandidates,
                    review_status = :reviewStatus,
                    feedback = :feedback,
                    updated_at = :updatedAt
                WHERE candidate_id = :candidateId
                """, candidateParams(candidate));
        if (updated > 0) {
            return candidate;
        }
        template.update("""
                INSERT INTO tb_skill_candidate
                    (candidate_id, source_chunk_id, source_type, source_id, term, normalized_term,
                     search_text, skill_type, action, technology, target, evidence_text, context, difficulty,
                     extraction_method, confidence_detail, source_position, normalization_info, mapping_candidates,
                     review_status, feedback, status, confidence, occurrence_count, matched_skill_id, reviewer_note,
                     created_at, updated_at)
                VALUES
                    (:candidateId, :sourceChunkId, :sourceType, :sourceId, :term, :normalizedTerm,
                     :searchText, :skillType, :action, :technology, :target, :evidenceText, :context, :difficulty,
                     :extractionMethod, :confidenceDetail, :sourcePosition, :normalizationInfo, :mappingCandidates,
                     :reviewStatus, :feedback, :status, :confidence, :occurrenceCount, :matchedSkillId, :reviewerNote,
                     :createdAt, :updatedAt)
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
    public List<SkillCandidate> findMissingEmbeddings(
            String embeddingProvider,
            String embeddingModel,
            int limit) {
        int max = limit <= 0 ? 100 : limit;
        return template.query("""
                SELECT c.*
                FROM tb_skill_candidate c
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_skill_embedding e
                    WHERE e.source_type = 'SKILL_CANDIDATE'
                      AND e.source_id = c.candidate_id
                      AND e.embedding_provider = :embeddingProvider
                      AND e.embedding_model = :embeddingModel
                )
                ORDER BY c.created_at DESC, c.candidate_id
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("embeddingProvider", embeddingProvider)
                .addValue("embeddingModel", embeddingModel)
                .addValue("limit", max), this::mapCandidate);
    }

    @Override
    public int countMissingEmbeddings(String embeddingProvider, String embeddingModel) {
        Integer count = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_candidate c
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_skill_embedding e
                    WHERE e.source_type = 'SKILL_CANDIDATE'
                      AND e.source_id = c.candidate_id
                      AND e.embedding_provider = :embeddingProvider
                      AND e.embedding_model = :embeddingModel
                )
                """, new MapSqlParameterSource()
                .addValue("embeddingProvider", embeddingProvider)
                .addValue("embeddingModel", embeddingModel), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<SkillEmbeddingMetadata> findEmbeddingMetadataList(String candidateId) {
        return template.query("""
                SELECT embedding_provider, embedding_model, embedding_dimension, created_at
                FROM tb_skill_embedding
                WHERE source_type = 'SKILL_CANDIDATE' AND source_id = :candidateId
                ORDER BY created_at DESC
                """, Map.of("candidateId", candidateId), (rs, rowNum) -> new SkillEmbeddingMetadata(
                rs.getString("embedding_provider"),
                rs.getString("embedding_model"),
                rs.getInt("embedding_dimension"),
                instant(rs.getTimestamp("created_at"))));
    }

    @Override
    public SkillCandidate saveEmbedding(
            String candidateId,
            String embeddingProvider,
            String embeddingModel,
            int embeddingDimension,
            String embeddingText,
            List<Double> embedding) {
        String normalizedCandidateId = normalize(candidateId);
        if (normalizedCandidateId == null) {
            throw new IllegalArgumentException("candidateId must not be blank");
        }
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        String normalizedText = normalize(embeddingText);
        if (normalizedText == null) {
            throw new IllegalArgumentException("embeddingText must not be blank");
        }
        Instant now = Instant.now();
        String vector = vectorLiteral(embedding);
        template.update("""
                INSERT INTO tb_skill_embedding
                    (embedding_id, source_type, source_id, embedding_provider, embedding_model,
                     embedding_dimension, embedding_text, embedding, created_at, updated_at)
                VALUES
                    (:embeddingId, 'SKILL_CANDIDATE', :candidateId, :embeddingProvider, :embeddingModel,
                     :embeddingDimension, :embeddingText, CAST(:embedding AS vector), :now, :now)
                ON CONFLICT (source_type, source_id, embedding_provider, embedding_model)
                DO UPDATE SET embedding_dimension = EXCLUDED.embedding_dimension,
                              embedding_text = EXCLUDED.embedding_text,
                              embedding = EXCLUDED.embedding,
                              updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource()
                .addValue("embeddingId", "ske_" + normalizedCandidateId)
                .addValue("candidateId", normalizedCandidateId)
                .addValue("embeddingProvider", embeddingProvider)
                .addValue("embeddingModel", embeddingModel)
                .addValue("embeddingDimension", embeddingDimension)
                .addValue("embeddingText", normalizedText)
                .addValue("embedding", vector)
                .addValue("now", Timestamp.from(now)));
        template.update("""
                UPDATE tb_skill_candidate
                SET embedding = CAST(:embedding AS vector),
                    updated_at = :now
                WHERE candidate_id = :candidateId
                """, new MapSqlParameterSource()
                .addValue("candidateId", normalizedCandidateId)
                .addValue("embedding", vector)
                .addValue("now", Timestamp.from(now)));
        return findCandidate(normalizedCandidateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill candidate: " + normalizedCandidateId));
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
        CandidateSearchFilter filter = CandidateSearchFilter.from(q);
        String query = filter.query();
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
            sql.append("""
                      AND (LOWER(term) LIKE :likeQ
                           OR normalized_term LIKE :likeQ
                           OR LOWER(COALESCE(search_text, '')) LIKE :likeQ
                           OR LOWER(COALESCE(target, '')) LIKE :likeQ)
                    """);
            params.addValue("likeQ", "%" + query + "%");
        }
        if (!filter.skillType().isBlank()) {
            sql.append("  AND LOWER(COALESCE(skill_type, '')) = :skillType\n");
            params.addValue("skillType", filter.skillType());
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
                """ + candidateOrderBy(pageable.getSort()) + """
                LIMIT :limit OFFSET :offset
                """, params, this::mapCandidate);
        Long total = template.queryForObject("""
                SELECT COUNT(*)
                """ + sql,
                params,
                Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private String candidateOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "ORDER BY created_at DESC\n";
        }
        List<String> orders = sort.stream()
                .map(order -> {
                    String column = candidateSortColumn(order.getProperty());
                    return column == null ? null : column + (order.isDescending() ? " DESC" : " ASC");
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return orders.isEmpty() ? "ORDER BY created_at DESC\n" : "ORDER BY " + String.join(", ", orders) + "\n";
    }

    private String candidateSortColumn(String property) {
        return switch (property) {
            case "candidateId", "candidate_id" -> "candidate_id";
            case "term", "rawText", "raw_text" -> "term";
            case "normalizedTerm", "normalizedText", "normalized_term" -> "normalized_term";
            case "searchText", "search_text" -> "search_text";
            case "skillType", "skill_type" -> "skill_type";
            case "difficulty" -> "difficulty";
            case "embedded" -> "CASE WHEN embedding IS NULL THEN 0 ELSE 1 END";
            case "status" -> "status";
            case "matchedSkillName", "matched_skill_name", "matchedSkillId", "matched_skill_id" -> "matched_skill_id";
            case "similarityScore", "similarity_score" -> "similarity_score";
            case "confidenceScore", "confidence", "confidence_score" -> "confidence";
            case "createdAt", "created_at" -> "created_at";
            case "updatedAt", "updated_at" -> "updated_at";
            default -> null;
        };
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
                .addValue("searchText", candidate.searchText())
                .addValue("skillType", candidate.skillType())
                .addValue("action", candidate.action())
                .addValue("technology", writeStringList(candidate.technology()))
                .addValue("target", candidate.target())
                .addValue("evidenceText", candidate.evidenceText())
                .addValue("context", candidate.context())
                .addValue("difficulty", candidate.difficulty())
                .addValue("extractionMethod", candidate.extractionMethod())
                .addValue("confidenceDetail", candidate.confidenceDetail())
                .addValue("sourcePosition", candidate.sourcePosition())
                .addValue("normalizationInfo", candidate.normalizationInfo())
                .addValue("mappingCandidates", candidate.mappingCandidates())
                .addValue("reviewStatus", candidate.reviewStatus())
                .addValue("feedback", candidate.feedback())
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
                rs.getString("search_text"),
                rs.getString("skill_type"),
                rs.getString("action"),
                readStringList(rs.getString("technology")),
                rs.getString("target"),
                rs.getString("evidence_text"),
                rs.getString("context"),
                rs.getString("difficulty"),
                rs.getString("extraction_method"),
                rs.getString("confidence_detail"),
                rs.getString("source_position"),
                rs.getString("normalization_info"),
                rs.getString("mapping_candidates"),
                rs.getString("review_status"),
                rs.getString("feedback"),
                rs.getObject("embedding") != null,
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

    private String writeStringList(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize skill candidate technology", ex);
        }
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String vectorLiteral(List<Double> embedding) {
        return embedding.toString();
    }

    private record CandidateSearchFilter(String query, String skillType) {
        private static CandidateSearchFilter from(String q) {
            if (q == null || q.isBlank()) {
                return new CandidateSearchFilter("", "");
            }
            String query = q.trim().toLowerCase();
            String marker = "skilltype:";
            int markerIndex = query.indexOf(marker);
            if (markerIndex < 0) {
                return new CandidateSearchFilter(query, "");
            }
            String before = query.substring(0, markerIndex).trim();
            String value = query.substring(markerIndex + marker.length()).trim();
            int nextSpace = value.indexOf(' ');
            String skillType = nextSpace < 0 ? value : value.substring(0, nextSpace);
            String after = nextSpace < 0 ? "" : value.substring(nextSpace + 1).trim();
            return new CandidateSearchFilter((before + " " + after).trim(), skillType);
        }
    }
}
