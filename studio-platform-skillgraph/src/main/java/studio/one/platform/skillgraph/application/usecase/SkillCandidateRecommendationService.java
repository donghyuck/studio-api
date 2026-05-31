package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.SkillCandidateRecommendationJobCommand;
import studio.one.platform.skillgraph.application.command.SkillRecommendationApplyCommand;
import studio.one.platform.skillgraph.application.result.SkillRecommendationApplyResult;
import studio.one.platform.skillgraph.application.result.SkillRecommendationJobView;
import studio.one.platform.skillgraph.application.result.SkillRecommendationResultView;

public interface SkillCandidateRecommendationService {

    String SERVICE_NAME = "skillCandidateRecommendationService";

    SkillRecommendationJobView createJob(SkillCandidateRecommendationJobCommand command);

    Page<SkillRecommendationJobView> searchJobs(Pageable pageable);

    SkillRecommendationJobView getJob(String jobId);

    List<SkillRecommendationResultView> getJobResults(String jobId);

    Page<SkillRecommendationResultView> getJobResults(String jobId, Pageable pageable);

    List<SkillRecommendationResultView> getCandidateResults(String candidateId);

    SkillRecommendationApplyResult applyResult(String resultId, SkillRecommendationApplyCommand command);

    SkillRecommendationApplyResult applyJob(String jobId, SkillRecommendationApplyCommand command);
}
