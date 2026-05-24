package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillCandidateAutoApproveResult(
        int requestedCount,
        int approvedCount,
        int skippedCount,
        List<SkillCandidateView> approved,
        List<SkillCandidateAutoApproveSkip> skipped) {
}
