package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.command.PreviewSkillCategoryRelationsCommand;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryRelationsCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryGraphView;
import studio.one.platform.skillgraph.application.result.SkillCategoryRelationView;

public interface SkillCategoryRelationService {

    String SERVICE_NAME = "skillCategoryRelationService";

    SkillCategoryGraphView preview(PreviewSkillCategoryRelationsCommand command);

    List<SkillCategoryRelationView> saveRelations(SaveSkillCategoryRelationsCommand command);

    List<SkillCategoryRelationView> findRelations(List<String> categoryIds);

    void deleteRelation(String relationId);
}
