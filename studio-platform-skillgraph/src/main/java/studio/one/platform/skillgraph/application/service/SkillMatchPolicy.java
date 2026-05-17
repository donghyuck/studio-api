package studio.one.platform.skillgraph.application.service;

/**
 * 스킬 매칭 정책을 정의하는 레코드.
 */
public record SkillMatchPolicy(
        double matchThreshold,
        double aliasThreshold) {

    public SkillMatchPolicy {
        matchThreshold = clamp(matchThreshold, 0.0d, 1.0d);
        aliasThreshold = clamp(aliasThreshold, 0.0d, matchThreshold);
    }

    public static SkillMatchPolicy defaults() {
        return new SkillMatchPolicy(0.92d, 0.82d);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
