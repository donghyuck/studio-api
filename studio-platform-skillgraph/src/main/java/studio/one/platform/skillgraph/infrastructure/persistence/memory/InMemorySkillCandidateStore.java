package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStats;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;

public class InMemorySkillCandidateStore implements SkillCandidateStore {

    private final Map<String, SkillSourceChunk> sourceChunks = new ConcurrentHashMap<>();
    private final Map<String, SkillCandidate> candidates = new ConcurrentHashMap<>();
    private final Map<String, String> candidateIdByNormalizedTerm = new ConcurrentHashMap<>();
    private final Map<String, String> candidateIdBySourceAndNormalizedTerm = new ConcurrentHashMap<>();

    @Override
    public SkillSourceChunk saveSourceChunk(SkillSourceChunk chunk) {
        sourceChunks.put(chunk.sourceChunkId(), chunk);
        return chunk;
    }

    @Override
    public SkillCandidate saveCandidate(SkillCandidate candidate) {
        candidates.put(candidate.candidateId(), candidate);
        candidateIdByNormalizedTerm.put(candidate.normalizedTerm(), candidate.candidateId());
        SkillSourceChunk chunk = sourceChunks.get(candidate.sourceChunkId());
        candidateIdBySourceAndNormalizedTerm.put(sourceKey(
                candidate.sourceType(),
                candidate.sourceId(),
                chunk == null ? null : chunk.chunkId(),
                candidate.normalizedTerm()), candidate.candidateId());
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
    public List<SkillCandidateStats> findCandidateStatsBySkillIds(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return skillIds.stream()
                .map(skillId -> {
                    List<SkillCandidate> matched = candidates.values().stream()
                            .filter(candidate -> skillId.equals(candidate.matchedSkillId()))
                            .toList();
                    int occurrences = matched.stream().mapToInt(SkillCandidate::occurrenceCount).sum();
                    double confidence = matched.stream()
                            .mapToDouble(SkillCandidate::confidence)
                            .max()
                            .orElse(0.0d);
                    return new SkillCandidateStats(skillId, occurrences, confidence);
                })
                .filter(stats -> stats.occurrenceCount() > 0 || stats.confidenceScore() > 0)
                .toList();
    }

    @Override
    public Optional<SkillCandidate> findCandidateBySourceAndNormalizedTerm(
            String sourceType,
            String sourceId,
            String chunkId,
            String normalizedTerm) {
        return Optional.ofNullable(candidateIdBySourceAndNormalizedTerm.get(sourceKey(
                sourceType,
                sourceId,
                chunkId,
                normalizedTerm)))
                .map(candidates::get);
    }

    @Override
    public Page<SkillCandidate> searchCandidates(
            SkillCandidateStatus status,
            String q,
            String sourceType,
            String sourceId,
            Pageable pageable) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<SkillCandidate> filtered = candidates.values().stream()
                .filter(candidate -> status == null || candidate.status() == status)
                .filter(candidate -> sourceType == null || sourceType.isBlank() || sourceType.trim().equals(candidate.sourceType()))
                .filter(candidate -> sourceId == null || sourceId.isBlank() || sourceId.trim().equals(candidate.sourceId()))
                .filter(candidate -> query.isBlank()
                        || candidate.normalizedTerm().contains(query)
                        || candidate.term().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(SkillCandidate::createdAt).reversed())
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), filtered.size()));
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    public List<SkillSourceChunk> sourceChunks() {
        return new ArrayList<>(sourceChunks.values());
    }

    private String sourceKey(String sourceType, String sourceId, String chunkId, String normalizedTerm) {
        return normalize(sourceType) + "|" + normalize(sourceId) + "|" + normalize(chunkId) + "|" + normalizedTerm;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
