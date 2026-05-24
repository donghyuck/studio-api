package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStats;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;

public interface SkillCandidateStore {

    String SERVICE_NAME = "skillCandidateStore";

    SkillSourceChunk saveSourceChunk(SkillSourceChunk chunk);

    SkillCandidate saveCandidate(SkillCandidate candidate);

    Optional<SkillCandidate> findCandidate(String candidateId);

    Optional<SkillCandidate> findCandidateByNormalizedTerm(String normalizedTerm);

    default List<SkillCandidateStats> findCandidateStatsBySkillIds(List<String> skillIds) {
        return List.of();
    }

    default Optional<SkillCandidate> findCandidateBySourceAndNormalizedTerm(
            String sourceType,
            String sourceId,
            String chunkId,
            String normalizedTerm) {
        return Optional.empty();
    }

    Page<SkillCandidate> searchCandidates(
            SkillCandidateStatus status,
            String q,
            String sourceType,
            String sourceId,
            Pageable pageable);

    @Deprecated(forRemoval = true)
    default List<SkillCandidate> searchCandidates(SkillCandidateStatus status, String q, int limit) {
        return searchCandidates(status, q, null, null, Pageable.ofSize(limit <= 0 ? 100 : limit)).getContent();
    }

    @Deprecated(forRemoval = true)
    default List<SkillCandidate> searchCandidates(
            SkillCandidateStatus status,
            String q,
            String sourceType,
            String sourceId,
            int limit) {
        return searchCandidates(status, q, sourceType, sourceId, Pageable.ofSize(limit <= 0 ? 100 : limit))
                .getContent();
    }
}
