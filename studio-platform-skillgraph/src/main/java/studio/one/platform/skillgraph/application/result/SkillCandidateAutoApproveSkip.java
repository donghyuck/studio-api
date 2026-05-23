package studio.one.platform.skillgraph.application.result;

public record SkillCandidateAutoApproveSkip(
        String candidateId,
        String reason,
        Double confidence,
        Double similarityScore) {
}
