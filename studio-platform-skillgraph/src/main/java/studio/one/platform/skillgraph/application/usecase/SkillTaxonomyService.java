package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.AssignCategoryFromClusterCommand;
import studio.one.platform.skillgraph.application.command.AssignSkillsToCategoryCommand;
import studio.one.platform.skillgraph.application.command.BulkSkillCategoryCommand;
import studio.one.platform.skillgraph.application.command.MergeSkillCategoriesCommand;
import studio.one.platform.skillgraph.application.command.MoveSkillCategoryCommand;
import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryHistoryView;
import studio.one.platform.skillgraph.application.result.SkillCategoryMutationResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;

public interface SkillTaxonomyService {

    String SERVICE_NAME = "skillTaxonomyService";

    SkillCategoryView saveCategory(SkillCategoryCommand command);

    List<SkillCategoryView> findCategories(String parentCategoryId);

    Page<SkillCategoryView> searchCategories(String q, String parentCategoryId, Pageable pageable);

    SkillCategoryView getCategory(String categoryId);

    void deleteCategory(String categoryId);

    SkillCategoryView moveCategory(String categoryId, MoveSkillCategoryCommand command);

    SkillCategoryMutationResult assignSkills(String categoryId, AssignSkillsToCategoryCommand command);

    SkillCategoryMutationResult assignFromCluster(String categoryId, AssignCategoryFromClusterCommand command);

    SkillCategoryMutationResult mergeCategories(MergeSkillCategoriesCommand command);

    SkillCategoryMutationResult bulk(BulkSkillCategoryCommand command);

    Page<SkillCategoryHistoryView> findCategoryHistory(String categoryId, Pageable pageable);

    Page<SkillCategoryHistoryView> findSkillCategoryHistory(String skillId, Pageable pageable);
}
