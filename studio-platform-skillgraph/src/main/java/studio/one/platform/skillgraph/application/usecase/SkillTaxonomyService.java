package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;

public interface SkillTaxonomyService {

    String SERVICE_NAME = "skillTaxonomyService";

    SkillCategoryView saveCategory(SkillCategoryCommand command);

    List<SkillCategoryView> findCategories(String parentCategoryId);
}
