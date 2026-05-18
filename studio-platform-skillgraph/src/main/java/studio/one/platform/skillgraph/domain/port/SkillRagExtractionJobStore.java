package studio.one.platform.skillgraph.domain.port;

import java.util.List;
import java.util.Optional;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;

public interface SkillRagExtractionJobStore {

    String SERVICE_NAME = "skillRagExtractionJobStore";

    SkillRagExtractionJob saveJob(SkillRagExtractionJob job);

    Optional<SkillRagExtractionJob> findJob(String jobId);

    SkillRagExtractionJobItem saveItem(SkillRagExtractionJobItem item);

    List<SkillRagExtractionJobItem> listItems(String jobId, int offset, int limit);

    List<SkillRagExtractionJobItem> listItemsByStatus(
            String jobId,
            SkillRagExtractionItemStatus status,
            int limit);
}
