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

    List<SkillCandidate> searchCandidates(SkillCandidateStatus status, String q, int limit);
}
