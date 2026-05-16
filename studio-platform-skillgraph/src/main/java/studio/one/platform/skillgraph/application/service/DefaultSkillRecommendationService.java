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

@RequiredArgsConstructor
public class DefaultSkillRecommendationService implements SkillRecommendationService {
    private final SkillMappingStore store;

    @Override
    public List<CourseRecommendationView> recommendCourses(List<String> targetSkillIds, List<String> ownedSkillIds, int limit) {
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
        Set<String> matched = mappings.stream().map(CourseSkillMapping::skillId).collect(Collectors.toSet());
        Set<String> remaining = new HashSet<>(missing);
        remaining.removeAll(matched);
        double matchedWeight = mappings.stream()
                .filter(mapping -> missing.contains(mapping.skillId()))
                .mapToDouble(CourseSkillMapping::weight)
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
