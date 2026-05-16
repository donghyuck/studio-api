package studio.one.platform.skillgraph.application.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

@RequiredArgsConstructor
public class DefaultSkillTaxonomyService implements SkillTaxonomyService {

    private final SkillTaxonomyStore store;

    @Override
    public SkillCategoryView saveCategory(SkillCategoryCommand command) {
        return SkillCategoryView.from(store.saveCategory(new SkillCategory(
                command.categoryId(), command.parentCategoryId(), command.name(), command.displayOrder())));
    }

    @Override
    public List<SkillCategoryView> findCategories(String parentCategoryId) {
        return store.findCategories(parentCategoryId).stream()
                .map(SkillCategoryView::from)
                .toList();
    }
}
