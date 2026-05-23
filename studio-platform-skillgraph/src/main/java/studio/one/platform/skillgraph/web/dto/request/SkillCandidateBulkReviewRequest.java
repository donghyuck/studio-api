package studio.one.platform.skillgraph.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;

public record SkillCandidateBulkReviewRequest(
        @NotEmpty
        @Size(max = 500)
        List<@NotBlank @Size(max = 100) String> candidateIds,
        @NotNull SkillCandidateStatus status,
        boolean generateEmbedding,
        @Size(max = 1000)
        String reviewerNote) {
}
