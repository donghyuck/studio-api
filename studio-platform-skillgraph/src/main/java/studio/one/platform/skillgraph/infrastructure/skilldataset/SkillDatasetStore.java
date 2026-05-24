package studio.one.platform.skillgraph.infrastructure.skilldataset;

import java.util.List;
import java.util.Optional;

public interface SkillDatasetStore {

    void saveDataset(SkillDataset dataset);

    void upsertConcept(SkillConcept concept);

    void upsertRelation(SkillRelation relation);

    void upsertConcepts(List<SkillConcept> concepts);

    void upsertRelations(List<SkillRelation> relations);

    Optional<SkillDataset> findDataset(String datasetId);

    Optional<SkillConcept> findConcept(String conceptId);

    List<SkillConcept> findConcepts(String datasetId, String conceptType, int limit);

    List<SkillRelation> findRelations(String datasetId, String relationType, int limit);

}
