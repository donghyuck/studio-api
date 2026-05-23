package studio.one.platform.skillgraph.application.command;

import java.util.List;

public record SkillCandidateAutoApproveCommand(
        List<String> candidateIds,
        Double minConfidence,
        Double minSimilarityScore,
        boolean generateEmbedding,
        String reviewerNote) {
}
