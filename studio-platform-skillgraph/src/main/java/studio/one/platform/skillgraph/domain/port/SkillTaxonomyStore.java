package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCategoryHistory;

public interface SkillTaxonomyStore {

    String SERVICE_NAME = "skillTaxonomyStore";

    SkillCategory saveCategory(SkillCategory category);

    Optional<SkillCategory> findCategory(String categoryId);

    List<SkillCategory> findCategories(String parentCategoryId);

    default Page<SkillCategory> searchCategories(String q, String parentCategoryId, Pageable pageable) {
        return new org.springframework.data.domain.PageImpl<>(findCategories(parentCategoryId), pageable,
                findCategories(parentCategoryId).size());
    }

    default void deleteCategory(String categoryId) {
        throw new UnsupportedOperationException("Skill category deletion is not implemented");
    }

    default List<SkillCategory> findDescendants(String categoryId) {
        return List.of();
    }

    default SkillCategoryHistory saveHistory(SkillCategoryHistory history) {
        return history;
    }

    default Page<SkillCategoryHistory> findCategoryHistory(String categoryId, Pageable pageable) {
        return org.springframework.data.domain.Page.empty(pageable);
    }

    default Page<SkillCategoryHistory> findSkillCategoryHistory(String skillId, Pageable pageable) {
        return org.springframework.data.domain.Page.empty(pageable);
    }
}
