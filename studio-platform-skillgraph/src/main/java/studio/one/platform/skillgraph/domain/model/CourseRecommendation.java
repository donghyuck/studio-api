package studio.one.platform.skillgraph.domain.model;

import java.util.List;

public record CourseRecommendation(String courseId, double score, List<String> matchedSkillIds, List<String> missingSkillIds) {

    public CourseRecommendation {
        courseId = courseId == null ? "" : courseId.trim();
        score = Math.max(0.0d, Math.min(1.0d, score));
        matchedSkillIds = matchedSkillIds == null ? List.of() : List.copyOf(matchedSkillIds);
        missingSkillIds = missingSkillIds == null ? List.of() : List.copyOf(missingSkillIds);
    }
}
