package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.skillgraph.application.command.SkillCandidateRecommendationJobCommand;
import studio.one.platform.skillgraph.application.command.SkillRecommendationApplyCommand;
import studio.one.platform.skillgraph.application.result.SkillRecommendationApplyResult;
import studio.one.platform.skillgraph.application.result.SkillRecommendationJobView;
import studio.one.platform.skillgraph.application.result.SkillRecommendationResultView;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateRecommendationService;
import studio.one.platform.skillgraph.web.controller.SkillRecommendationMgmtController;
import studio.one.platform.skillgraph.web.dto.request.SkillCandidateRecommendationJobRequest;

class SkillRecommendationMgmtControllerTest {

    @Test
    void returnsBadRequestWithMessageWhenRecommendationPreconditionFails() {
        SkillRecommendationMgmtController controller = new SkillRecommendationMgmtController(
                new FailingRecommendationService("자동 분석은 후보 임베딩 생성 이후에 가능합니다."));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.createJob(new SkillCandidateRecommendationJobRequest(
                        "SELECTED",
                        List.of("skc_1"),
                        null,
                        null,
                        null,
                        null,
                        "kure",
                        "nlpai-lab/KURE-v1",
                        1024,
                        List.of("SKILL_DICTIONARY"),
                        5,
                        0.75d,
                        0.80d,
                        0.92d)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("자동 분석은 후보 임베딩 생성 이후에 가능합니다.", ex.getReason());
    }

    private static final class FailingRecommendationService implements SkillCandidateRecommendationService {

        private final String message;

        private FailingRecommendationService(String message) {
            this.message = message;
        }

        @Override
        public SkillRecommendationJobView createJob(SkillCandidateRecommendationJobCommand command) {
            throw new IllegalArgumentException(message);
        }

        @Override
        public Page<SkillRecommendationJobView> searchJobs(Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SkillRecommendationJobView getJob(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SkillRecommendationResultView> getJobResults(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<SkillRecommendationResultView> getJobResults(String jobId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SkillRecommendationResultView> getCandidateResults(String candidateId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SkillRecommendationApplyResult applyResult(String resultId, SkillRecommendationApplyCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SkillRecommendationApplyResult applySelectedResults(
                List<String> resultIds,
                SkillRecommendationApplyCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SkillRecommendationApplyResult applyJob(String jobId, SkillRecommendationApplyCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
