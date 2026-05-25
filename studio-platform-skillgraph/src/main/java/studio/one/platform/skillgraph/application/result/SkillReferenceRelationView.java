package studio.one.platform.skillgraph.application.result;

import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillRelation;

public record SkillReferenceRelationView(
        String relationId,
        String datasetId,
        String provider,
        String sourceConceptId,
        String targetConceptId,
        String relationType,
        Double confidence
) {

    public static SkillReferenceRelationView from(SkillRelation relation) {
        return new SkillReferenceRelationView(
                relation.relationId(),
                relation.datasetId(),
                relation.provider(),
                relation.sourceConceptId(),
                relation.targetConceptId(),
                relation.relationType(),
                relation.confidence());
    }
}
