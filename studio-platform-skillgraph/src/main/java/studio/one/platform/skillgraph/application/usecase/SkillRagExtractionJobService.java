package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;

public interface SkillRagExtractionJobService {

    String SERVICE_NAME = "skillRagExtractionJobService";

    SkillRagExtractionJob submitAllChunks(String objectType, String objectId, String documentId, Integer limit);

    default SkillRagExtractionJob submitAllChunks(
            String objectType,
            String objectId,
            String documentId,
            Integer limit,
            boolean excludeExtracted,
            boolean generateEmbeddings,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension) {
        return submitAllChunks(objectType, objectId, documentId, limit);
    }

    default SkillRagExtractionJob submit(
            String objectType,
            String objectId,
            String documentId,
            String query,
            List<String> chunkIds,
            Integer limit,
            boolean excludeExtracted,
            boolean generateEmbeddings,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension) {
        return submitAllChunks(objectType, objectId, documentId, limit, excludeExtracted, generateEmbeddings,
                embeddingProvider, embeddingModel, embeddingDimension);
    }

    SkillRagExtractionJob getJob(String jobId);

    List<SkillRagExtractionJob> listJobs(
            String status,
            String objectType,
            String objectId,
            String documentId,
            int offset,
            int limit);

    Page<SkillRagExtractionJob> searchJobs(
            String status,
            String objectType,
            String objectId,
            String documentId,
            Pageable pageable);

    List<SkillRagExtractionJobItem> listItems(String jobId, int offset, int limit);

    Page<SkillCandidateView> listCandidates(String jobId, Pageable pageable);

    SkillRagExtractionJob retryFailed(String jobId);

    int recoverStaleJobs();
}
