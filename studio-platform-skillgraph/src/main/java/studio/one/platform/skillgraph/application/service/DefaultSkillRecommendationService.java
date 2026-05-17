package studio.one.platform.skillgraph.application.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.result.CourseRecommendationView;
import studio.one.platform.skillgraph.application.usecase.SkillRecommendationService;
import studio.one.platform.skillgraph.domain.model.CourseRecommendation;
import studio.one.platform.skillgraph.domain.model.CourseSkillMapping;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;

/**
 * 스킬 기반 코스 추천 유스케이스 구현체.
 *
 * 주요 역할:
 * - 사용자의 보유 스킬과 목표 스킬을 기반으로 코스 추천
 * - 추천 결과는 매칭된 스킬과 누락된 스킬, 그리고 추천 점수로 구성
 *
 * 핵심 처리 흐름:
 * 1. 입력된 목표 스킬과 보유 스킬을 정규화하여 집합으로 변환
 * 2. 목표 스킬에서 보유한 스킬을 제외하여 누락된 스킬 집합 생성
 * 3. 누락된 스킬을 포함하는 코스 매핑 정보를 조회하여 코스별 매칭된 스킬과 가중치 계산
 * 4. 매칭된 스킬의 가중치 합산을 통해 코스별 추천 점수 계산
 * 5. 추천 점수를 기준으로 코스 목록 정렬 및 제한된 수 만큼 반환
 *
 * @author donghyuck, son
 * @since 2026-05-17
 */
@RequiredArgsConstructor
public class DefaultSkillRecommendationService implements SkillRecommendationService {
    private final SkillMappingStore store;

    @Override
    public List<CourseRecommendationView> recommendCourses(List<String> targetSkillIds, List<String> ownedSkillIds,
            int limit) {
        Set<String> targets = normalize(targetSkillIds);
        Set<String> owned = normalize(ownedSkillIds);
        Set<String> missing = new HashSet<>(targets);
        missing.removeAll(owned);
        if (missing.isEmpty()) {
            return List.of();
        }
        Map<String, List<CourseSkillMapping>> byCourse = store.findCoursesBySkillIds(List.copyOf(missing)).stream()
                .collect(Collectors.groupingBy(CourseSkillMapping::courseId));
        int max = limit <= 0 ? 10 : Math.min(limit, 100);
        return byCourse.entrySet().stream()
                .map(entry -> recommend(entry.getKey(), entry.getValue(), missing))
                .sorted(Comparator.comparingDouble(CourseRecommendation::score).reversed()
                        .thenComparing(CourseRecommendation::courseId))
                .limit(max)
                .map(CourseRecommendationView::from)
                .toList();
    }

    private CourseRecommendation recommend(String courseId, List<CourseSkillMapping> mappings, Set<String> missing) {
        Map<String, Double> weightBySkill = mappings.stream()
                .filter(mapping -> missing.contains(mapping.skillId()))
                .collect(Collectors.toMap(
                        CourseSkillMapping::skillId,
                        CourseSkillMapping::weight,
                        Math::max));
        Set<String> matched = weightBySkill.keySet();
        Set<String> remaining = new HashSet<>(missing);
        remaining.removeAll(matched);
        double matchedWeight = weightBySkill.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        double score = missing.isEmpty() ? 0.0d : Math.min(1.0d, matchedWeight / missing.size());
        return new CourseRecommendation(courseId, score, List.copyOf(matched), List.copyOf(remaining));
    }

    private Set<String> normalize(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}
