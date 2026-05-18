package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatch;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatchType;
import studio.one.platform.skillgraph.domain.model.SkillVectorItem;

public interface SkillDictionaryStore {

    String SERVICE_NAME = "skillDictionaryStore";

    SkillDictionary save(SkillDictionary skill);

    Optional<SkillDictionary> findById(String skillId);

    Optional<SkillDictionary> findByNormalizedName(String normalizedName);

    default Optional<SkillDictionaryMatch> findMatchByNormalizedTerm(String normalizedTerm) {
        Optional<SkillDictionary> exact = findByNormalizedName(normalizedTerm);
        if (exact.isPresent()) {
            return Optional.of(new SkillDictionaryMatch(exact.get(), 1.0d, SkillDictionaryMatchType.EXACT));
        }
        return findByNormalizedAlias(normalizedTerm)
                .map(skill -> new SkillDictionaryMatch(skill, 1.0d, SkillDictionaryMatchType.ALIAS));
    }

    default Optional<SkillDictionary> findByNormalizedAlias(String normalizedAlias) {
        return Optional.empty();
    }

    default Optional<SkillDictionaryMatch> findNearestByEmbedding(List<Double> embedding, double minScore) {
        return Optional.empty();
    }

    default SkillAlias saveAlias(SkillAlias alias) {
        throw new UnsupportedOperationException("Skill alias persistence is not implemented");
    }

    default List<SkillVectorItem> findVectorItems(int limit) {
        return List.of();
    }

    default List<SkillDictionary> findMissingEmbeddingSkills(int limit) {
        return List.of();
    }

    default int countMissingEmbeddingSkills() {
        return findMissingEmbeddingSkills(Integer.MAX_VALUE).size();
    }

    default SkillDictionary saveEmbedding(String skillId, List<Double> embedding, String embeddingModel) {
        throw new UnsupportedOperationException("Skill embedding persistence is not implemented");
    }

    List<SkillDictionary> search(String q, int limit);

    default List<SkillDictionary> search(String q, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 100 : limit;
        return search(q, safeOffset + safeLimit).stream()
                .skip(safeOffset)
                .limit(safeLimit)
                .toList();
    }
}
