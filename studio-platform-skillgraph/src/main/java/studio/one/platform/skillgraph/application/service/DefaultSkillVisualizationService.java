package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.core.vector.visualization.UmapVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.skillgraph.application.result.SkillClusterView;
import studio.one.platform.skillgraph.application.result.SkillProjectionPointView;
import studio.one.platform.skillgraph.application.result.SkillProjectionResult;
import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;
import studio.one.platform.skillgraph.application.usecase.SkillVisualizationService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillVectorItem;
import studio.one.platform.skillgraph.domain.port.SkillClusterer;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;

/**
 * 스킬 시각화 유스케이스 구현체.
 *
 * 주요 역할:
 * - 스킬 벡터 데이터를 2D 공간에 투영
 * - 투영된 데이터 클러스터링
 * - 시각화 결과 제공
 *
 * 핵심 처리 흐름:
 * 1. Skill Dictionary에서 벡터 아이템 조회
 * 2. UMAP 기반 2D 투영 생성
 * 3. 투영 결과 클러스터링
 * 4. Projection/Cluster 저장 및 반환
 *
 * 현재 구조는 단일 projectionId에 대해 전체 스킬을 투영하는 방식이지만, 향후 사용자별 맞춤 투영, 실시간 업데이트, 다양한 projection 알고리즘 지원 등으로 확장 가능하다.
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
public class DefaultSkillVisualizationService implements SkillVisualizationService {

    private final SkillDictionaryStore dictionaryStore;
    private final SkillProjectionStore projectionStore;
    private final SkillClusterer clusterer;
    private final UmapVectorProjectionGenerator projectionGenerator;

    public DefaultSkillVisualizationService(
            SkillDictionaryStore dictionaryStore,
            SkillProjectionStore projectionStore,
            SkillClusterer clusterer) {
        this(dictionaryStore, projectionStore, clusterer, new UmapVectorProjectionGenerator());
    }

    @Override
    public SkillProjectionResult generateProjection(String projectionId, int limit) {
        String resolvedProjectionId = normalizeProjectionId(projectionId);
        int max = normalizeLimit(limit, 1000);
        Instant now = Instant.now();
        List<SkillVectorItem> skillItems = dictionaryStore.findVectorItems(max);
        List<VectorItem> vectorItems = skillItems.stream()
                .map(item -> new VectorItem(
                        item.skillId(),
                        "skill",
                        item.skillId(),
                        item.label(),
                        item.label(),
                        item.embedding(),
                        item.embeddingModel(),
                        item.embedding().size(),
                        Map.of("skillId", item.skillId()),
                        item.createdAt()))
                .toList();
        List<SkillProjection> points = projectionGenerator.generate(resolvedProjectionId, vectorItems, now).stream()
                .map(this::toSkillProjection)
                .toList();
        SkillClusterer.SkillClusteringResult clustered = clusterer.cluster(resolvedProjectionId, points);
        projectionStore.replaceProjection(resolvedProjectionId, clustered.projections(), clustered.clusters());
        return new SkillProjectionResult(
                resolvedProjectionId,
                clustered.projections().size(),
                clustered.clusters().size(),
                clustered.projections().stream().map(SkillProjectionPointView::from).toList(),
                clustered.clusters().stream().map(SkillClusterView::from).toList());
    }

    @Override
    public List<SkillProjectionSummaryView> listProjections(int limit, int offset) {
        return projectionStore.listProjections(normalizeLimit(limit, 100), Math.max(0, offset)).stream()
                .map(SkillProjectionSummaryView::from)
                .toList();
    }

    @Override
    public List<SkillProjectionPointView> findProjectionPoints(String projectionId, String clusterId, int limit, int offset) {
        return projectionStore.findProjectionPoints(projectionId, clusterId, normalizeLimit(limit, 100), Math.max(0, offset)).stream()
                .map(SkillProjectionPointView::from)
                .toList();
    }

    @Override
    public List<SkillClusterView> findClusters(String projectionId) {
        return projectionStore.findClusters(projectionId).stream()
                .map(SkillClusterView::from)
                .toList();
    }

    private SkillProjection toSkillProjection(VectorProjectionPoint point) {
        return new SkillProjection(
                point.projectionId(),
                point.vectorItemId(),
                point.x(),
                point.y(),
                point.clusterId(),
                point.displayOrder() == null ? 0 : point.displayOrder(),
                point.createdAt());
    }

    private String normalizeProjectionId(String projectionId) {
        if (projectionId == null || projectionId.isBlank()) {
            return "skp_" + UUID.randomUUID();
        }
        return projectionId.trim();
    }

    private int normalizeLimit(int limit, int defaultLimit) {
        if (limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, SkillGraphLimits.MAX_PROJECTION_ITEMS);
    }
}
