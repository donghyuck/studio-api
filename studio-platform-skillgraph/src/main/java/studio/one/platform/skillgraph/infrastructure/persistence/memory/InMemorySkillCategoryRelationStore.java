package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.domain.model.SkillCategoryRelation;
import studio.one.platform.skillgraph.domain.port.SkillCategoryRelationStore;

public class InMemorySkillCategoryRelationStore implements SkillCategoryRelationStore {

    private final Map<String, SkillCategoryRelation> relations = new ConcurrentHashMap<>();

    @Override
    public SkillCategoryRelation save(SkillCategoryRelation relation) {
        relations.put(relation.relationId(), relation);
        return relation;
    }

    @Override
    public Optional<SkillCategoryRelation> findById(String relationId) {
        return Optional.ofNullable(relations.get(relationId));
    }

    @Override
    public List<SkillCategoryRelation> findByCategoryIds(List<String> categoryIds) {
        List<String> ids = categoryIds == null ? List.of() : categoryIds;
        return relations.values().stream()
                .filter(relation -> ids.isEmpty()
                        || ids.contains(relation.sourceCategoryId())
                        || ids.contains(relation.targetCategoryId()))
                .toList();
    }

    @Override
    public void delete(String relationId) {
        relations.remove(relationId);
    }
}
