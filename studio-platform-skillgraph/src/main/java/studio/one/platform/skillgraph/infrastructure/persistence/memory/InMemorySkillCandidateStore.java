package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;

public class InMemorySkillCandidateStore implements SkillCandidateStore {

    private final Map<String, SkillSourceChunk> sourceChunks = new ConcurrentHashMap<>();
    private final Map<String, SkillCandidate> candidates = new ConcurrentHashMap<>();
    private final Map<String, String> candidateIdByNormalizedTerm = new ConcurrentHashMap<>();

    @Override
    public SkillSourceChunk saveSourceChunk(SkillSourceChunk chunk) {
        sourceChunks.put(chunk.sourceChunkId(), chunk);
        return chunk;
    }

    @Override
    public SkillCandidate saveCandidate(SkillCandidate candidate) {
        candidates.put(candidate.candidateId(), candidate);
        candidateIdByNormalizedTerm.put(candidate.normalizedTerm(), candidate.candidateId());
        return candidate;
    }

    @Override
    public Optional<SkillCandidate> findCandidate(String candidateId) {
        return Optional.ofNullable(candidates.get(candidateId));
    }

    @Override
    public Optional<SkillCandidate> findCandidateByNormalizedTerm(String normalizedTerm) {
        return Optional.ofNullable(candidateIdByNormalizedTerm.get(normalizedTerm))
                .map(candidates::get);
    }

    @Override
    public List<SkillCandidate> searchCandidates(SkillCandidateStatus status, String q, int limit) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int max = limit <= 0 ? 100 : limit;
        return candidates.values().stream()
                .filter(candidate -> status == null || candidate.status() == status)
                .filter(candidate -> query.isBlank()
                        || candidate.normalizedTerm().contains(query)
                        || candidate.term().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(SkillCandidate::createdAt).reversed())
                .limit(max)
                .toList();
    }

    public List<SkillSourceChunk> sourceChunks() {
        return new ArrayList<>(sourceChunks.values());
    }
}
