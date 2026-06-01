package studio.one.platform.skillgraph.domain.model;

public record SkillClusterMember(
        String clusterId,
        String skillId,
        String embeddingId,
        String projectionId,
        double membershipScore,
        double distanceToCentroid,
        boolean representative) {

    public SkillClusterMember {
        clusterId = requireText(clusterId, "clusterId");
        skillId = requireText(skillId, "skillId");
        embeddingId = normalize(embeddingId);
        projectionId = requireText(projectionId, "projectionId");
        membershipScore = Math.max(0.0d, Math.min(1.0d, membershipScore));
        distanceToCentroid = Math.max(0.0d, distanceToCentroid);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
