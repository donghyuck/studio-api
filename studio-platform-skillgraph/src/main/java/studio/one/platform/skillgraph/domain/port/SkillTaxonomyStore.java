package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.skillgraph.domain.model.SkillCategory;

public interface SkillTaxonomyStore {

    String SERVICE_NAME = "skillTaxonomyStore";

    SkillCategory saveCategory(SkillCategory category);

    Optional<SkillCategory> findCategory(String categoryId);

    List<SkillCategory> findCategories(String parentCategoryId);
}
