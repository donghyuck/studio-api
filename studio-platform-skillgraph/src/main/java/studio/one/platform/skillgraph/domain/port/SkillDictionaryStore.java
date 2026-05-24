package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    Page<SkillDictionary> search(String q, Pageable pageable);

    default List<SkillDictionary> findByCategoryId(String categoryId, int limit) {
        return List.of();
    }

    default int countByCategoryId(String categoryId) {
        return findByCategoryId(categoryId, Integer.MAX_VALUE).size();
    }

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

    default int updateCategory(List<String> skillIds, String categoryId) {
        throw new UnsupportedOperationException("Skill category assignment is not implemented");
    }

    default int updateCategoryByCategoryIds(List<String> sourceCategoryIds, String targetCategoryId) {
        throw new UnsupportedOperationException("Skill category merge is not implemented");
    }

    default int updateStatus(List<String> skillIds, String status) {
        throw new UnsupportedOperationException("Skill status bulk update is not implemented");
    }

    @Deprecated(forRemoval = true)
    default List<SkillDictionary> search(String q, int limit) {
        return search(q, Pageable.ofSize(limit <= 0 ? 100 : limit)).getContent();
    }

}
