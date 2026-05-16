package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

public class InMemorySkillTaxonomyStore implements SkillTaxonomyStore {

    private final Map<String, SkillCategory> categories = new ConcurrentHashMap<>();

    @Override
    public SkillCategory saveCategory(SkillCategory category) {
        categories.put(category.categoryId(), category);
        return category;
    }

    @Override
    public Optional<SkillCategory> findCategory(String categoryId) {
        return Optional.ofNullable(categories.get(categoryId));
    }

    @Override
    public List<SkillCategory> findCategories(String parentCategoryId) {
        String parent = parentCategoryId == null || parentCategoryId.isBlank() ? null : parentCategoryId.trim();
        return categories.values().stream()
                .filter(category -> parent == null
                        ? category.parentCategoryId() == null
                        : parent.equals(category.parentCategoryId()))
                .sorted(Comparator.comparingInt(SkillCategory::displayOrder).thenComparing(SkillCategory::name))
                .toList();
    }
}
