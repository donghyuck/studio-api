package studio.one.platform.skillgraph.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJob;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingResult;
import studio.one.platform.skillgraph.application.result.SkillDictionaryView;

public interface SkillDictionaryService {

    String SERVICE_NAME = "skillDictionaryService";

    default Page<SkillDictionaryView> search(String q, Pageable pageable) {
        return search(q, null, null, pageable);
    }

    Page<SkillDictionaryView> search(String q, String status, String categoryId, Pageable pageable);

    SkillDictionaryView create(CreateSkillDictionaryCommand command);

    default SkillDictionaryEmbeddingResult embedMissing(int limit) {
        return embedMissing(null, null, 0, limit);
    }

    SkillDictionaryEmbeddingResult embedMissing(
            String embeddingProvider,
            String embeddingModel,
            int embeddingDimension,
            int limit);

    SkillDictionaryEmbeddingJob getEmbeddingJob(String jobId);

    SkillDictionaryView get(String skillId);
}
