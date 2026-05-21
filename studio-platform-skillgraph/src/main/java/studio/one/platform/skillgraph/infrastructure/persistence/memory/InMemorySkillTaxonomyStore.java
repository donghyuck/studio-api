package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCategoryHistory;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

public class InMemorySkillTaxonomyStore implements SkillTaxonomyStore {

    private final Map<String, SkillCategory> categories = new ConcurrentHashMap<>();
    private final Map<String, SkillCategoryHistory> histories = new ConcurrentHashMap<>();

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

    @Override
    public Page<SkillCategory> searchCategories(String q, String parentCategoryId, Pageable pageable) {
        String query = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        String parent = parentCategoryId == null || parentCategoryId.isBlank() ? null : parentCategoryId.trim();
        List<SkillCategory> filtered = categories.values().stream()
                .filter(category -> parent == null
                        ? category.parentCategoryId() == null
                        : parent.equals(category.parentCategoryId()))
                .filter(category -> query.isBlank()
                        || category.name().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || category.categoryId().toLowerCase(java.util.Locale.ROOT).contains(query))
                .sorted(Comparator.comparingInt(SkillCategory::displayOrder).thenComparing(SkillCategory::name))
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), filtered.size()));
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Override
    public void deleteCategory(String categoryId) {
        categories.remove(categoryId);
    }

    @Override
    public List<SkillCategory> findDescendants(String categoryId) {
        List<SkillCategory> direct = findCategories(categoryId);
        return direct.stream()
                .flatMap(category -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(category),
                        findDescendants(category.categoryId()).stream()))
                .toList();
    }

    @Override
    public SkillCategoryHistory saveHistory(SkillCategoryHistory history) {
        histories.put(history.historyId(), history);
        return history;
    }

    @Override
    public Page<SkillCategoryHistory> findCategoryHistory(String categoryId, Pageable pageable) {
        return historyPage(histories.values().stream()
                .filter(history -> categoryId.equals(history.categoryId()))
                .toList(), pageable);
    }

    @Override
    public Page<SkillCategoryHistory> findSkillCategoryHistory(String skillId, Pageable pageable) {
        return historyPage(histories.values().stream()
                .filter(history -> skillId.equals(history.skillId()))
                .toList(), pageable);
    }

    private Page<SkillCategoryHistory> historyPage(List<SkillCategoryHistory> source, Pageable pageable) {
        List<SkillCategoryHistory> filtered = source.stream()
                .sorted(Comparator.comparing(SkillCategoryHistory::createdAt).reversed())
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), filtered.size()));
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }
}
