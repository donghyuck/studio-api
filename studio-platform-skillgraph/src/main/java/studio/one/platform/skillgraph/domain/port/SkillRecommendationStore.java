package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillRecommendationJob;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResult;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResultStatus;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationTargetHit;

public interface SkillRecommendationStore {

    String SERVICE_NAME = "skillRecommendationStore";

    SkillRecommendationJob saveJob(SkillRecommendationJob job);

    Optional<SkillRecommendationJob> findJob(String jobId);

    Page<SkillRecommendationJob> searchJobs(Pageable pageable);

    SkillRecommendationResult saveResult(SkillRecommendationResult result);

    Optional<SkillRecommendationResult> findResult(String resultId);

    Page<SkillRecommendationResult> findResultsByJob(String jobId, Pageable pageable);

    default List<SkillRecommendationResult> findResultsByJob(String jobId) {
        return findResultsByJob(jobId, Pageable.ofSize(1000)).getContent();
    }

    List<SkillRecommendationResult> findResultsByCandidate(String candidateId);

    int updateResultStatus(
            String resultId,
            SkillRecommendationResultStatus status,
            String applyType,
            String appliedBy,
            String reason);

    List<Double> findEmbedding(String sourceType, String sourceId, String provider, String model, int dimension);

    List<SkillRecommendationTargetHit> findNearestEmbeddings(
            String sourceType,
            String provider,
            String model,
            int dimension,
            List<Double> embedding,
            String excludedSourceId,
            int limit,
            double minScore);
}
