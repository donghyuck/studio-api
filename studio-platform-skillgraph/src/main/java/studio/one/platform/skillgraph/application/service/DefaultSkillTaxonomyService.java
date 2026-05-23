package studio.one.platform.skillgraph.application.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCategoryHistory;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

/**
 * 스킬 분류 관리 유스케이스 구현체.
 *
 * 주요 역할:
 * - 스킬 카테고리 저장
 * - 스킬 카테고리 조회
 *
 * 핵심 처리 흐름:
 * 1. 카테고리 저장 시 유효성 검증 (예: 이름 중복, 부모 카테고리 존재 여부)
 * 2. 계층적 카테고리 구조 지원 (parentCategoryId 활용)
 * 3. 카테고리 조회 시 부모-자식 관계 반영
 *
 * 현재 구조는 단일 레벨의 카테고리 관리에 초점을 맞추고 있으며, 향후 다중 레벨 계층 구조 및 트리 탐색 기능이 추가될 수 있다.
 *
 * @author donghyuck, son
 * @since 2026-05-17
 *
 *        <pre>
 *
 * &lt;&lt; 개정이력(Modification Information) &gt;&gt;
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2026-05-17  donghyuck, son: 최초 생성.
 *        </pre>
 */
public class DefaultSkillTaxonomyService implements SkillTaxonomyService {

    private final SkillTaxonomyStore store;
    private final SkillDictionaryStore dictionaryStore;
    private final SkillProjectionStore projectionStore;

    public DefaultSkillTaxonomyService(SkillTaxonomyStore store) {
        this(store, null, null);
    }

    public DefaultSkillTaxonomyService(
            SkillTaxonomyStore store,
            SkillDictionaryStore dictionaryStore,
            SkillProjectionStore projectionStore) {
        this.store = store;
        this.dictionaryStore = dictionaryStore;
        this.projectionStore = projectionStore;
    }

    @Override
    public SkillCategoryView saveCategory(SkillCategoryCommand command) {
        String categoryId = normalize(command.categoryId());
        SkillCategory existing = categoryId == null ? null : store.findCategory(categoryId).orElse(null);
        SkillCategory saved = store.saveCategory(new SkillCategory(
                categoryId == null ? newCategoryId() : categoryId,
                normalize(command.parentCategoryId()),
                command.name(),
                command.displayOrder()));
        saveHistory(saved.categoryId(), null, existing == null ? "CREATE" : "UPDATE",
                existing == null ? null : existing.parentCategoryId(), saved.parentCategoryId(), saved.name());
        return categoryView(saved);
    }

    @Override
    public List<SkillCategoryView> findCategories(String parentCategoryId) {
        return store.findCategories(parentCategoryId).stream()
                .map(this::categoryView)
                .toList();
    }

    @Override
    public Page<SkillCategoryView> searchCategories(String q, String parentCategoryId, Pageable pageable) {
        return store.searchCategories(q, parentCategoryId, pageable)
                .map(this::categoryView);
    }

    @Override
    public SkillCategoryView getCategory(String categoryId) {
        return categoryView(requireCategory(categoryId));
    }

    @Override
    public void deleteCategory(String categoryId) {
        SkillCategory category = requireCategory(categoryId);
        store.deleteCategory(category.categoryId());
        saveHistory(category.categoryId(), null, "DELETE", category.parentCategoryId(), null, category.name());
    }

    @Override
    public SkillCategoryView moveCategory(String categoryId, MoveSkillCategoryCommand command) {
        SkillCategory category = requireCategory(categoryId);
        String parentCategoryId = normalize(command.parentCategoryId());
        if (category.categoryId().equals(parentCategoryId)) {
            throw new IllegalArgumentException("category cannot be moved below itself");
        }
        if (parentCategoryId != null) {
            requireCategory(parentCategoryId);
            boolean descendant = store.findDescendants(category.categoryId()).stream()
                    .anyMatch(child -> child.categoryId().equals(parentCategoryId));
            if (descendant) {
                throw new IllegalArgumentException("category cannot be moved below its descendant");
            }
        }
        SkillCategory moved = store.saveCategory(new SkillCategory(
                category.categoryId(),
                parentCategoryId,
                category.name(),
                command.displayOrder()));
        saveHistory(moved.categoryId(), null, "MOVE", category.parentCategoryId(), moved.parentCategoryId(),
                "displayOrder=" + moved.displayOrder());
        return categoryView(moved);
    }

    @Override
    public SkillCategoryMutationResult assignSkills(String categoryId, AssignSkillsToCategoryCommand command) {
        SkillCategory category = requireCategory(categoryId);
        List<String> skillIds = normalizeIds(command.skillIds());
        java.util.Map<String, String> previousCategoryIds = new java.util.LinkedHashMap<>();
        for (String skillId : skillIds) {
            previousCategoryIds.put(skillId, dictionaryStore().findById(skillId)
                    .map(skill -> skill.categoryId())
                    .orElse(null));
        }
        int affected = dictionaryStore().updateCategory(skillIds, category.categoryId());
        for (String skillId : skillIds) {
            saveHistory(category.categoryId(), skillId, "ASSIGN_SKILL", previousCategoryIds.get(skillId),
                    category.categoryId(), null);
        }
        return new SkillCategoryMutationResult(category.categoryId(), affected);
    }

    @Override
    public SkillCategoryMutationResult assignFromCluster(String categoryId, AssignCategoryFromClusterCommand command) {
        SkillCategory category = requireCategory(categoryId);
        String clusterId = normalize(command.clusterId());
        if (!Boolean.TRUE.equals(command.includeNoise()) && isNoise(clusterId)) {
            return new SkillCategoryMutationResult(category.categoryId(), 0);
        }
        List<String> skillIds = projectionStore().findProjectionPoints(
                normalize(command.projectionId()),
                clusterId,
                PageRequest.of(0, SkillGraphLimits.MAX_PROJECTION_ITEMS))
                .getContent()
                .stream()
                .map(SkillProjection::skillId)
                .distinct()
                .toList();
        if (skillIds.isEmpty()) {
            return new SkillCategoryMutationResult(category.categoryId(), 0);
        }
        return assignSkills(category.categoryId(), new AssignSkillsToCategoryCommand(skillIds));
    }

    @Override
    public SkillCategoryMutationResult mergeCategories(MergeSkillCategoriesCommand command) {
        SkillCategory target = requireCategory(command.targetCategoryId());
        List<String> sourceIds = normalizeIds(command.sourceCategoryIds()).stream()
                .filter(sourceId -> !sourceId.equals(target.categoryId()))
                .toList();
        for (String sourceId : sourceIds) {
            requireCategory(sourceId);
        }
        int affected = dictionaryStore().updateCategoryByCategoryIds(sourceIds, target.categoryId());
        for (String sourceId : sourceIds) {
            for (SkillCategory child : store.findCategories(sourceId)) {
                store.saveCategory(new SkillCategory(child.categoryId(), target.categoryId(), child.name(),
                        child.displayOrder()));
            }
            saveHistory(target.categoryId(), null, "MERGE", sourceId, target.categoryId(), null);
            if (Boolean.TRUE.equals(command.deleteSources())) {
                store.deleteCategory(sourceId);
            }
        }
        return new SkillCategoryMutationResult(target.categoryId(), affected);
    }

    @Override
    public SkillCategoryMutationResult bulk(BulkSkillCategoryCommand command) {
        String operation = normalize(command.operation()).toUpperCase(Locale.ROOT);
        if ("ASSIGN_SKILLS".equals(operation)) {
            return assignSkills(command.categoryId(), new AssignSkillsToCategoryCommand(command.ids()));
        }
        if ("UPDATE_SKILL_STATUS".equals(operation)) {
            int affected = dictionaryStore().updateStatus(normalizeIds(command.ids()), required(command.status(), "status"));
            return new SkillCategoryMutationResult(command.categoryId(), affected);
        }
        if ("MOVE_CATEGORIES".equals(operation)) {
            int affected = 0;
            for (String categoryId : normalizeIds(command.ids())) {
                moveCategory(categoryId, new MoveSkillCategoryCommand(command.parentCategoryId(),
                        command.displayOrder() == null ? 0 : command.displayOrder()));
                affected++;
            }
            return new SkillCategoryMutationResult(command.parentCategoryId(), affected);
        }
        throw new IllegalArgumentException("Unsupported bulk operation: " + command.operation());
    }

    @Override
    public Page<SkillCategoryHistoryView> findCategoryHistory(String categoryId, Pageable pageable) {
        return store.findCategoryHistory(required(categoryId, "categoryId"), pageable)
                .map(SkillCategoryHistoryView::from);
    }

    @Override
    public Page<SkillCategoryHistoryView> findSkillCategoryHistory(String skillId, Pageable pageable) {
        return store.findSkillCategoryHistory(required(skillId, "skillId"), pageable)
                .map(SkillCategoryHistoryView::from);
    }

    private SkillCategory requireCategory(String categoryId) {
        String id = required(categoryId, "categoryId");
        return store.findCategory(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + id));
    }

    private SkillCategoryView categoryView(SkillCategory category) {
        int skillCount = dictionaryStore == null ? 0 : dictionaryStore.countByCategoryId(category.categoryId());
        return SkillCategoryView.from(category, skillCount);
    }

    private SkillDictionaryStore dictionaryStore() {
        if (dictionaryStore == null) {
            throw new IllegalStateException("SkillDictionaryStore is required");
        }
        return dictionaryStore;
    }

    private SkillProjectionStore projectionStore() {
        if (projectionStore == null) {
            throw new IllegalStateException("SkillProjectionStore is required");
        }
        return projectionStore;
    }

    private void saveHistory(
            String categoryId,
            String skillId,
            String actionType,
            String previousCategoryId,
            String newCategoryId,
            String detail) {
        store.saveHistory(new SkillCategoryHistory(
                "sch_" + UUID.randomUUID().toString().replace("-", ""),
                categoryId,
                skillId,
                actionType,
                previousCategoryId,
                newCategoryId,
                detail,
                java.time.Instant.now()));
    }

    private List<String> normalizeIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String newCategoryId() {
        return "cat_" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isNoise(String clusterId) {
        return clusterId != null && ("noise".equalsIgnoreCase(clusterId)
                || clusterId.toLowerCase(Locale.ROOT).contains("noise"));
    }
}
