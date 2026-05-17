package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;

public interface SkillRagExtractionJobService {

    String SERVICE_NAME = "skillRagExtractionJobService";

    SkillRagExtractionJob submitAllChunks(String objectType, String objectId, String documentId, Integer limit);

    SkillRagExtractionJob getJob(String jobId);

    List<SkillRagExtractionJobItem> listItems(String jobId, int offset, int limit);

    SkillRagExtractionJob retryFailed(String jobId);
}
