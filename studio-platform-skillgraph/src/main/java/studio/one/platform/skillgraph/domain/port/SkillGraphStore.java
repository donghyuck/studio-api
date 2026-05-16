package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.skillgraph.domain.model.SkillRelation;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;

public interface SkillGraphStore {

    String SERVICE_NAME = "skillGraphStore";

    SkillRelation saveRelation(SkillRelation relation);

    Optional<SkillRelation> findRelation(String relationId);

    List<SkillRelation> findRelations(String skillId, SkillRelationType type);
}
