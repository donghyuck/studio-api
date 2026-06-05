package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobStatus;

public interface SkillRagExtractionJobStore {

    String SERVICE_NAME = "skillRagExtractionJobStore";

    SkillRagExtractionJob saveJob(SkillRagExtractionJob job);

    Optional<SkillRagExtractionJob> findJob(String jobId);

    List<SkillRagExtractionJob> listJobs(
            SkillRagExtractionJobStatus status,
            String objectType,
            String objectId,
            String documentId,
            int offset,
            int limit);

    Page<SkillRagExtractionJob> searchJobs(
            SkillRagExtractionJobStatus status,
            String objectType,
            String objectId,
            String documentId,
            Pageable pageable);

    SkillRagExtractionJobItem saveItem(SkillRagExtractionJobItem item);

    List<SkillRagExtractionJobItem> listItems(String jobId, int offset, int limit);

    List<SkillRagExtractionJobItem> listItemsByStatus(
            String jobId,
            SkillRagExtractionItemStatus status,
            int limit);

    Set<String> findSuccessfulChunkIds(
            String objectType,
            String objectId,
            String documentId,
            String excludedJobId);
}
