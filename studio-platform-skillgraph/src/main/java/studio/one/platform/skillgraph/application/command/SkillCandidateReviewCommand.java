package studio.one.platform.skillgraph.application.command;

import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;

public record SkillCandidateReviewCommand(
        SkillCandidateStatus status,
        String matchedSkillId,
        String reviewerNote) {
}
