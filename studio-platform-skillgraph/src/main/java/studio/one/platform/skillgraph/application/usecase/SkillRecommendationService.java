package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.result.CourseRecommendationView;

public interface SkillRecommendationService {
    String SERVICE_NAME = "skillRecommendationService";

    List<CourseRecommendationView> recommendCourses(List<String> targetSkillIds, List<String> ownedSkillIds, int limit);
}
