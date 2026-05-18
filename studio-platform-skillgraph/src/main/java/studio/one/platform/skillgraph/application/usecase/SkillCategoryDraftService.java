package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.command.SaveSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;

public interface SkillCategoryDraftService {

    String SERVICE_NAME = "skillCategoryDraftService";

    SkillCategoryDraftResult generateDrafts(String projectionId, int representativeLimit);

    List<SkillCategoryView> saveDrafts(SaveSkillCategoryDraftCommand command);
}
