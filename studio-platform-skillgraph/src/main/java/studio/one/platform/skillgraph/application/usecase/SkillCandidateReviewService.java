package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;

public interface SkillCandidateReviewService {

    String SERVICE_NAME = "skillCandidateReviewService";

    List<SkillCandidateView> search(SkillCandidateStatus status, String q, int limit);

    default List<SkillCandidateView> search(SkillCandidateStatus status, String q, String sourceType, String sourceId, int limit) {
        return search(status, q, limit);
    }

    SkillCandidateView get(String candidateId);

    SkillCandidateView review(String candidateId, SkillCandidateReviewCommand command);
}
