package studio.one.platform.skillgraph.application.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
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
