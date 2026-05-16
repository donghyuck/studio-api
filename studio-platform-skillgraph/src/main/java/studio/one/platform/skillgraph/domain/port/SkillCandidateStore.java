package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;

public interface SkillCandidateStore {

    String SERVICE_NAME = "skillCandidateStore";

    SkillSourceChunk saveSourceChunk(SkillSourceChunk chunk);

    SkillCandidate saveCandidate(SkillCandidate candidate);

    Optional<SkillCandidate> findCandidate(String candidateId);

    Optional<SkillCandidate> findCandidateByNormalizedTerm(String normalizedTerm);

    default Optional<SkillCandidate> findCandidateBySourceAndNormalizedTerm(
            String sourceType,
            String sourceId,
            String chunkId,
            String normalizedTerm) {
        return Optional.empty();
    }

    List<SkillCandidate> searchCandidates(SkillCandidateStatus status, String q, int limit);

    default List<SkillCandidate> searchCandidates(
            SkillCandidateStatus status,
            String q,
            String sourceType,
            String sourceId,
            int limit) {
        return searchCandidates(status, q, limit).stream()
                .filter(candidate -> sourceType == null || sourceType.isBlank() || sourceType.trim().equals(candidate.sourceType()))
                .filter(candidate -> sourceId == null || sourceId.isBlank() || sourceId.trim().equals(candidate.sourceId()))
                .toList();
    }
}
