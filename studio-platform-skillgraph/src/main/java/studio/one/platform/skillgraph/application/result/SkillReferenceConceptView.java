package studio.one.platform.skillgraph.application.result;

import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConcept;

public record SkillReferenceConceptView(
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
        String normalizedLabel
) {

    public static SkillReferenceConceptView from(SkillConcept concept) {
        return new SkillReferenceConceptView(
                concept.conceptId(),
                concept.datasetId(),
                concept.provider(),
                concept.conceptType(),
                concept.externalCode(),
                concept.parentCode(),
                concept.preferredLabel(),
                concept.description(),
                concept.levelValue(),
                concept.categoryPath(),
                concept.normalizedLabel());
    }
}
