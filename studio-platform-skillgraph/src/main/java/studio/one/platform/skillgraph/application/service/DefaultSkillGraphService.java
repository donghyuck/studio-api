package studio.one.platform.skillgraph.application.service;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillRelationCommand;
import studio.one.platform.skillgraph.application.result.SkillRelationView;
import studio.one.platform.skillgraph.application.usecase.SkillGraphService;
import studio.one.platform.skillgraph.domain.model.SkillRelation;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;
import studio.one.platform.skillgraph.domain.port.SkillGraphStore;

/**
 * 스킬 그래프 관리 유스케이스 구현체.
 *
 * 주요 역할:
 * - 스킬 간 관계 저장
 * - 스킬 관계 조회
 * - 그래프 탐색 지원 (향후)
 *
 * 핵심 처리 흐름:
 * 1. 관계 저장 시 유효성 검증 (예: 존재하는 스킬인지)
 * 2. 관계 유형에 따른 저장 로직 분기 (예: prerequisite, related)
 * 3. 관계 조회 시 필터링 및 정렬 적용
 * 4. 그래프 탐색 시 BFS/DFS 알고리즘 활용 (향후)
 *
 * 현재 구조는 단일 레벨의 스킬 관계 관리에 초점을 맞추고 있으며, 향후 다중 레벨 그래프 탐색 및 분석 기능이 추가될 수 있다.
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
public class DefaultSkillGraphService implements SkillGraphService {

    private final SkillGraphStore store;

    @Override
    public SkillRelationView saveRelation(SkillRelationCommand command) {
        String relationId = command.relationId() == null || command.relationId().isBlank()
                ? "skr_" + UUID.randomUUID()
                : command.relationId();
        return SkillRelationView.from(store.saveRelation(new SkillRelation(
                relationId, command.sourceSkillId(), command.targetSkillId(), command.type())));
    }

    @Override
    public List<SkillRelationView> findRelations(String skillId, SkillRelationType type) {
        return store.findRelations(skillId, type).stream()
                .map(SkillRelationView::from)
                .toList();
    }
}
