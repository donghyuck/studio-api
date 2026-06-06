package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;

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
            int offset,
            int limit);

    Page<SkillRagExtractionJob> searchJobs(
            SkillRagExtractionJobStatus status,
            String objectType,
            String objectId,
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
            String excludedJobId);

    boolean acquireLease(String jobId, String owner, Instant now, Duration leaseDuration, int maxAutoRetries);

    boolean renewLease(String jobId, String owner, Instant now, Duration leaseDuration);

    void releaseLease(String jobId, String owner);

    void resetRetryState(String jobId, Instant now);

    List<String> findRecoverableJobIds(Instant now, int limit);

    String executionStatus(String jobId, Instant now, int maxAutoRetries);
}
