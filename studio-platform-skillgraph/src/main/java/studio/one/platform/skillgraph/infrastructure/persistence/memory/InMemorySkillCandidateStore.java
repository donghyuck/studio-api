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
import org.springframework.data.domain.Sort;

import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStats;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillEmbeddingMetadata;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;

public class InMemorySkillCandidateStore implements SkillCandidateStore {

    private final Map<String, SkillSourceChunk> sourceChunks = new ConcurrentHashMap<>();
    private final Map<String, SkillCandidate> candidates = new ConcurrentHashMap<>();
    private final Map<String, String> candidateIdByNormalizedTerm = new ConcurrentHashMap<>();
    private final Map<String, String> candidateIdBySourceAndNormalizedTerm = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> embeddings = new ConcurrentHashMap<>();
    private final Map<String, SkillEmbeddingMetadata> embeddingMetadata = new ConcurrentHashMap<>();

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
    public List<SkillCandidate> findMissingEmbeddings(String embeddingProvider, String embeddingModel, int limit) {
        int max = limit <= 0 ? 100 : limit;
        return candidates.values().stream()
                .filter(candidate -> !embeddings.containsKey(embeddingKey(candidate.candidateId(), embeddingProvider, embeddingModel)))
                .sorted(Comparator.comparing(SkillCandidate::createdAt).reversed())
                .limit(max)
                .toList();
    }

    @Override
    public int countMissingEmbeddings(String embeddingProvider, String embeddingModel) {
        return (int) candidates.values().stream()
                .filter(candidate -> !embeddings.containsKey(embeddingKey(candidate.candidateId(), embeddingProvider, embeddingModel)))
                .count();
    }

    @Override
    public SkillCandidate saveEmbedding(
            String candidateId,
            String embeddingProvider,
            String embeddingModel,
            int embeddingDimension,
            String embeddingText,
            List<Double> embedding) {
        SkillCandidate candidate = candidates.get(candidateId);
        if (candidate == null) {
            throw new IllegalArgumentException("Unknown skill candidate: " + candidateId);
        }
        embeddings.put(embeddingKey(candidateId, embeddingProvider, embeddingModel),
                embedding == null ? List.of() : List.copyOf(embedding));
        embeddingMetadata.put(embeddingKey(candidateId, embeddingProvider, embeddingModel),
                new SkillEmbeddingMetadata(embeddingProvider, embeddingModel, embeddingDimension, java.time.Instant.now()));
        return candidate;
    }

    @Override
    public List<SkillEmbeddingMetadata> findEmbeddingMetadataList(String candidateId) {
        String prefix = normalize(candidateId) + "|";
        return embeddingMetadata.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
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
        CandidateSearchFilter filter = CandidateSearchFilter.from(q);
        String query = filter.query();
        List<SkillCandidate> filtered = candidates.values().stream()
                .filter(candidate -> status == null || candidate.status() == status)
                .filter(candidate -> sourceType == null || sourceType.isBlank() || sourceType.trim().equals(candidate.sourceType()))
                .filter(candidate -> sourceId == null || sourceId.isBlank() || sourceId.trim().equals(candidate.sourceId()))
                .filter(candidate -> filter.skillType().isBlank()
                        || filter.skillType().equalsIgnoreCase(candidate.skillType()))
                .filter(candidate -> query.isBlank()
                        || candidate.normalizedTerm().contains(query)
                        || candidate.term().toLowerCase(Locale.ROOT).contains(query)
                        || contains(candidate.searchText(), query)
                        || contains(candidate.target(), query))
                .sorted(candidateComparator(pageable.getSort()))
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

    private String embeddingKey(String candidateId, String embeddingProvider, String embeddingModel) {
        return normalize(candidateId) + "|" + normalize(embeddingProvider) + "|" + normalize(embeddingModel);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private Comparator<SkillCandidate> candidateComparator(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return Comparator.comparing(SkillCandidate::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        Comparator<SkillCandidate> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<SkillCandidate> next = comparatorFor(order.getProperty());
            if (next == null) {
                continue;
            }
            if (order.isDescending()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator == null
                ? Comparator.comparing(SkillCandidate::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                : comparator;
    }

    private Comparator<SkillCandidate> comparatorFor(String property) {
        Comparator<String> strings = Comparator.nullsLast(String::compareToIgnoreCase);
        return switch (property) {
            case "candidateId", "candidate_id" -> Comparator.comparing(SkillCandidate::candidateId, strings);
            case "term", "rawText", "raw_text" -> Comparator.comparing(SkillCandidate::term, strings);
            case "normalizedTerm", "normalizedText", "normalized_term" -> Comparator.comparing(SkillCandidate::normalizedTerm, strings);
            case "searchText", "search_text" -> Comparator.comparing(SkillCandidate::searchText, strings);
            case "skillType", "skill_type" -> Comparator.comparing(SkillCandidate::skillType, strings);
            case "difficulty" -> Comparator.comparing(SkillCandidate::difficulty, strings);
            case "embedded" -> Comparator.comparing(SkillCandidate::embedded);
            case "status" -> Comparator.comparing(candidate -> candidate.status().name(), strings);
            case "matchedSkillName", "matched_skill_name", "matchedSkillId", "matched_skill_id" -> Comparator.comparing(SkillCandidate::matchedSkillId, strings);
            case "similarityScore", "similarity_score" -> Comparator.comparing(candidate -> 0.0d);
            case "confidenceScore", "confidence", "confidence_score" -> Comparator.comparing(SkillCandidate::confidence);
            case "createdAt", "created_at" -> Comparator.comparing(SkillCandidate::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "updatedAt", "updated_at" -> Comparator.comparing(SkillCandidate::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
    }

    private record CandidateSearchFilter(String query, String skillType) {
        private static CandidateSearchFilter from(String q) {
            if (q == null || q.isBlank()) {
                return new CandidateSearchFilter("", "");
            }
            String query = q.trim().toLowerCase(Locale.ROOT);
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
