package studio.one.platform.skillgraph.application.service;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillRelationCommand;
import studio.one.platform.skillgraph.application.result.SkillRelationView;
import studio.one.platform.skillgraph.application.usecase.SkillGraphService;
import studio.one.platform.skillgraph.domain.model.SkillRelation;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;
import studio.one.platform.skillgraph.domain.port.SkillGraphStore;

@RequiredArgsConstructor
public class DefaultSkillGraphService implements SkillGraphService {

    private final SkillGraphStore store;

    @Override
    public SkillRelationView saveRelation(SkillRelationCommand command) {
        String relationId = command.relationId() == null || command.relationId().isBlank()
                ? "skr_" + UUID.randomUUID()
                : command.relationId();
        return SkillRelationView.from(store.saveRelation(new SkillRelation(
                relationId, command.sourceSkillId(), command.targetSkillId(), command.type())));
    }

    @Override
    public List<SkillRelationView> findRelations(String skillId, SkillRelationType type) {
        return store.findRelations(skillId, type).stream()
                .map(SkillRelationView::from)
                .toList();
    }
}
