package studio.one.platform.skillgraph.application.result;

public record SkillClusterRepresentativeView(
        String skillId,
        String skillName,
        String normalizedName,
        String clusterId,
        double x,
        double y,
        double centroidDistance,
        Integer occurrenceCount,
        Double confidenceScore,
        String categoryId,
        String status,
        double representativeScore) {
}
