package studio.one.platform.skillgraph.infrastructure.skilldataset;

public record SkillConcept(

        String conceptId,

        String datasetId,

        String provider,

        String conceptType,

        String externalCode,

        String parentCode,

        String preferredLabel,

        String description,

        String levelValue,

        String categoryPath,

        String normalizedLabel,

        String rawJson

) {

}
