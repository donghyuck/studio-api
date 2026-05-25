package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillReferenceRoadmapContextView(
        SkillReferenceConceptView competencyUnit,
        List<SkillReferenceConceptView> competencyElements,
        List<SkillReferenceConceptView> performanceCriteria,
        List<SkillReferenceConceptView> knowledgeSkillsAttitudes,
        List<SkillReferenceRelationView> relations
) {
}
