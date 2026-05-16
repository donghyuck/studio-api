package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.domain.model.SkillRelation;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;
import studio.one.platform.skillgraph.domain.port.SkillGraphStore;

public class InMemorySkillGraphStore implements SkillGraphStore {

    private final Map<String, SkillRelation> relations = new ConcurrentHashMap<>();

    @Override
    public SkillRelation saveRelation(SkillRelation relation) {
        relations.put(relation.relationId(), relation);
        return relation;
    }

    @Override
    public Optional<SkillRelation> findRelation(String relationId) {
        return Optional.ofNullable(relations.get(relationId));
    }

    @Override
    public List<SkillRelation> findRelations(String skillId, SkillRelationType type) {
        return relations.values().stream()
                .filter(relation -> skillId == null || skillId.isBlank()
                        || skillId.equals(relation.sourceSkillId())
                        || skillId.equals(relation.targetSkillId()))
                .filter(relation -> type == null || relation.type() == type)
                .sorted(Comparator.comparing(SkillRelation::createdAt).reversed())
                .toList();
    }
}
