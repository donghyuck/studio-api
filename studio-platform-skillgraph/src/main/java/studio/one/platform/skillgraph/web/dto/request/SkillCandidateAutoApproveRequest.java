package studio.one.platform.skillgraph.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SkillCandidateAutoApproveRequest(
        @NotEmpty
        @Size(max = 500)
        List<@NotBlank @Size(max = 100) String> candidateIds,
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double minConfidence,
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double minSimilarityScore,
        boolean generateEmbedding,
        @Size(max = 1000)
        String reviewerNote) {
}
