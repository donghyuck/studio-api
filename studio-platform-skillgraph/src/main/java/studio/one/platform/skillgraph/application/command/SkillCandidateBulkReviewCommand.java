package studio.one.platform.skillgraph.application.command;

import java.util.List;

import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;

public record SkillCandidateBulkReviewCommand(
        List<String> candidateIds,
        SkillCandidateStatus status,
        boolean generateEmbedding,
        String reviewerNote) {
}
