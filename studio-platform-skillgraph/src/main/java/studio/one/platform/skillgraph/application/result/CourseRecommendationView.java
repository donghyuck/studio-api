package studio.one.platform.skillgraph.application.result;

import java.util.List;

import studio.one.platform.skillgraph.domain.model.CourseRecommendation;

public record CourseRecommendationView(String courseId, double score, List<String> matchedSkillIds, List<String> missingSkillIds) {
    public static CourseRecommendationView from(CourseRecommendation recommendation) {
        return new CourseRecommendationView(recommendation.courseId(), recommendation.score(),
                recommendation.matchedSkillIds(), recommendation.missingSkillIds());
    }
}
