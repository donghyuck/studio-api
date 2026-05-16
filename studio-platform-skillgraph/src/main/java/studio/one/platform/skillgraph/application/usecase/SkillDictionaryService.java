package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillDictionaryView;

public interface SkillDictionaryService {

    String SERVICE_NAME = "skillDictionaryService";

    List<SkillDictionaryView> search(String q, int limit);

    SkillDictionaryView get(String skillId);
}
