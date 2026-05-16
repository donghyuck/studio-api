package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;

public record SkillCandidateReviewRequest(
        @NotNull SkillCandidateStatus status,
        @Size(max = 100)
        String matchedSkillId,
        @Size(max = 1000)
        String reviewerNote) {
}
