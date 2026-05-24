package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.SkillCandidateAutoApproveCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateBulkReviewCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.result.SkillCandidateAutoApproveResult;
import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;

public interface SkillCandidateReviewService {

    String SERVICE_NAME = "skillCandidateReviewService";

    Page<SkillCandidateView> search(
            SkillCandidateStatus status,
            String q,
            String sourceType,
            String sourceId,
            Pageable pageable);

    SkillCandidateView get(String candidateId);

    SkillCandidateView review(String candidateId, SkillCandidateReviewCommand command);

    List<SkillCandidateView> reviewAll(SkillCandidateBulkReviewCommand command);

    SkillCandidateAutoApproveResult autoApprove(SkillCandidateAutoApproveCommand command);
}
