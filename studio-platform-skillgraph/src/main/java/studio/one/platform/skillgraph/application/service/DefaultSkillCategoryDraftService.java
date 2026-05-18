package studio.one.platform.skillgraph.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftView;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryDraftService;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

@RequiredArgsConstructor
public class DefaultSkillCategoryDraftService implements SkillCategoryDraftService {

    private static final String NOISE_CLUSTER_ID = "noise";

    private final SkillProjectionStore projectionStore;
    private final SkillDictionaryStore dictionaryStore;
    private final SkillTaxonomyStore taxonomyStore;

    @Override
    public SkillCategoryDraftResult generateDrafts(String projectionId, int representativeLimit) {
        String resolvedProjectionId = required(projectionId, "projectionId");
        int maxRepresentatives = representativeLimit <= 0 ? 5 : Math.min(representativeLimit, 20);
        List<SkillCluster> clusters = projectionStore.findClusters(resolvedProjectionId);
        List<SkillCategoryDraftView> drafts = clusters.stream()
                .sorted(Comparator.comparing(SkillCluster::clusterId))
                .map(cluster -> draft(resolvedProjectionId, cluster, maxRepresentatives))
                .toList();
        int noiseCount = (int) drafts.stream().filter(SkillCategoryDraftView::noise).count();
        return new SkillCategoryDraftResult(resolvedProjectionId, drafts.size(), noiseCount, drafts);
    }

    @Override
    public List<SkillCategoryView> saveDrafts(SaveSkillCategoryDraftCommand command) {
        if (command == null || command.categories() == null || command.categories().isEmpty()) {
            throw new IllegalArgumentException("categories must not be empty");
        }
        return command.categories().stream()
                .map(item -> new SkillCategory(
                        normalize(item.categoryId()) == null ? "cat_" + UUID.randomUUID().toString().replace("-", "") : item.categoryId(),
                        normalize(item.parentCategoryId()),
                        required(item.name(), "name"),
                        item.displayOrder() == null ? 0 : item.displayOrder()))
                .map(taxonomyStore::saveCategory)
                .map(SkillCategoryView::from)
                .toList();
    }

    private SkillCategoryDraftView draft(String projectionId, SkillCluster cluster, int representativeLimit) {
        List<SkillProjection> points = projectionStore.findProjectionPoints(
                projectionId,
                cluster.clusterId(),
                representativeLimit,
                0);
        List<String> skillIds = points.stream().map(SkillProjection::skillId).toList();
        List<String> names = skillIds.stream()
                .map(skillId -> dictionaryStore.findById(skillId).map(SkillDictionary::name).orElse(skillId))
                .toList();
        boolean noise = isNoise(cluster);
        return new SkillCategoryDraftView(
                "draft_" + cluster.clusterId(),
                cluster.clusterId(),
                noise ? "미분류 스킬" : proposeName(names, cluster),
                noise ? 0.2d : confidence(cluster.itemCount()),
                noise,
                cluster.itemCount(),
                skillIds,
                names);
    }

    private String proposeName(List<String> names, SkillCluster cluster) {
        String joined = String.join(" ", names).toLowerCase(Locale.ROOT);
        if (joined.contains("security") || joined.contains("인증") || joined.contains("인가") || joined.contains("oauth")) {
            return "인증·인가 보안";
        }
        if (joined.contains("vue") || joined.contains("react") || joined.contains("frontend") || joined.contains("컴포넌트")) {
            return "프론트엔드 UI 개발";
        }
        if (joined.contains("postgresql") || joined.contains("데이터") || joined.contains("jpa") || joined.contains("hibernate")) {
            return "데이터 처리 및 저장";
        }
        if (joined.contains("docker") || joined.contains("kubernetes") || joined.contains("배포") || joined.contains("운영")) {
            return "배포 및 운영 자동화";
        }
        if (joined.contains("api") || joined.contains("spring") || joined.contains("backend")) {
            return "백엔드 API 개발";
        }
        return cluster.label() == null ? "스킬 카테고리 " + cluster.clusterId() : cluster.label();
    }

    private boolean isNoise(SkillCluster cluster) {
        return NOISE_CLUSTER_ID.equalsIgnoreCase(cluster.clusterId())
                || cluster.clusterId().toLowerCase(Locale.ROOT).contains("noise");
    }

    private double confidence(int itemCount) {
        return Math.min(0.95d, 0.55d + Math.min(itemCount, 40) / 100.0d);
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
}
