package studio.one.platform.skillgraph.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJob;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingResult;
import studio.one.platform.skillgraph.application.result.SkillDictionaryView;

public interface SkillDictionaryService {

    String SERVICE_NAME = "skillDictionaryService";

    Page<SkillDictionaryView> search(String q, Pageable pageable);

    SkillDictionaryView create(CreateSkillDictionaryCommand command);

    SkillDictionaryEmbeddingResult embedMissing(int limit);

    SkillDictionaryEmbeddingJob getEmbeddingJob(String jobId);

    SkillDictionaryView get(String skillId);
}
