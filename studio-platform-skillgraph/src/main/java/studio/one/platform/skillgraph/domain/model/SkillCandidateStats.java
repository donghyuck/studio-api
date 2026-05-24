package studio.one.platform.skillgraph.domain.model;

public record SkillCandidateStats(
        String skillId,
        int occurrenceCount,
        double confidenceScore) {

    public SkillCandidateStats {
        skillId = skillId == null ? null : skillId.trim();
        occurrenceCount = Math.max(0, occurrenceCount);
        confidenceScore = Math.max(0.0d, Math.min(1.0d, confidenceScore));
    }
}
