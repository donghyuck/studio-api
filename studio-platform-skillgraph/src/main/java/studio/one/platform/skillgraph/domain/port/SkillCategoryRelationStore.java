package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.skillgraph.domain.model.SkillCategoryRelation;

public interface SkillCategoryRelationStore {

    String SERVICE_NAME = "skillCategoryRelationStore";

    SkillCategoryRelation save(SkillCategoryRelation relation);

    Optional<SkillCategoryRelation> findById(String relationId);

    List<SkillCategoryRelation> findByCategoryIds(List<String> categoryIds);

    void delete(String relationId);
}
