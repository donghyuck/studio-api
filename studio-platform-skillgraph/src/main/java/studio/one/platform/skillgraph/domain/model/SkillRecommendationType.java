package studio.one.platform.skillgraph.domain.model;

public enum SkillRecommendationType {
    EXISTING_SKILL_MATCH,
    DUPLICATE_CANDIDATE,
    SIMILAR_CANDIDATE,
    NCS_MAPPING_CANDIDATE,
    NEW_SKILL_CANDIDATE,
    REVIEW_REQUIRED,
    LOW_CONFIDENCE
}
