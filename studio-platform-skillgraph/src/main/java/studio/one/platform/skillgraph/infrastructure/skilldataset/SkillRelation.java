package studio.one.platform.skillgraph.infrastructure.skilldataset;

public record SkillRelation(

        String relationId,

        String datasetId,

        String provider,

        String sourceConceptId,

        String targetConceptId,

        String relationType,

        Double confidence,

        String rawJson

) {

}
