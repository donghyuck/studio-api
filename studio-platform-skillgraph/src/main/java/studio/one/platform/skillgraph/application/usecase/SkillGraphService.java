package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import studio.one.platform.skillgraph.application.command.SkillRelationCommand;
import studio.one.platform.skillgraph.application.result.SkillRelationView;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;

public interface SkillGraphService {

    String SERVICE_NAME = "skillGraphService";

    SkillRelationView saveRelation(SkillRelationCommand command);

    List<SkillRelationView> findRelations(String skillId, SkillRelationType type);
}
