package studio.one.platform.skillgraph.infrastructure.skilldataset;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkillDatasetStore {

    void saveDataset(SkillDataset dataset);

    void upsertConcept(SkillConcept concept);

    void upsertRelation(SkillRelation relation);

    void upsertConcepts(List<SkillConcept> concepts);

    void upsertRelations(List<SkillRelation> relations);

    Optional<SkillDataset> findDataset(String datasetId);

    Page<SkillDataset> findDatasets(Pageable pageable);

    Optional<SkillConcept> findConcept(String conceptId);

    Optional<SkillConcept> findConcept(String datasetId, String conceptId);

    List<SkillConcept> findConcepts(String datasetId, String conceptType, int limit);

    Page<SkillConcept> findConcepts(String datasetId, String conceptType, Pageable pageable);

    List<SkillConcept> searchConcepts(String datasetId, String conceptType, String query, int limit);

    Page<SkillConcept> searchConcepts(String datasetId, String conceptType, String query, Pageable pageable);

    List<SkillConcept> findChildConcepts(String datasetId, String conceptId, String relationType, int limit);

    Page<SkillConcept> findChildConcepts(String datasetId, String conceptId, String relationType, Pageable pageable);

    List<SkillConcept> findConceptsByIds(String datasetId, List<String> conceptIds);

    List<SkillRelation> findRelations(String datasetId, String relationType, int limit);

    List<SkillRelation> findOutgoingRelations(String datasetId, String sourceConceptId, String relationType, int limit);

    default long countConcepts(String datasetId, String provider, String conceptType) {
        throw new UnsupportedOperationException("countConcepts is not supported");
    }

    default List<SkillConcept> findConceptsForEmbedding(
            String datasetId,
            String provider,
            String conceptType,
            int limit,
            long offset) {
        throw new UnsupportedOperationException("findConceptsForEmbedding is not supported");
    }

    default boolean conceptEmbeddingExists(
            String conceptId,
            String embeddingProvider,
            String embeddingModel,
            String textType,
            String sourceHash) {
        throw new UnsupportedOperationException("conceptEmbeddingExists is not supported");
    }

    default void deleteConceptEmbedding(
            String conceptId,
            String embeddingProvider,
            String embeddingModel,
            String textType) {
        throw new UnsupportedOperationException("deleteConceptEmbedding is not supported");
    }

    default void upsertConceptEmbedding(SkillConceptEmbedding embedding) {
        throw new UnsupportedOperationException("upsertConceptEmbedding is not supported");
    }

    default List<SkillConceptVectorSearchHit> vectorSearchConcepts(
            String datasetId,
            String provider,
            String conceptType,
            String embeddingProvider,
            String embeddingModel,
            String textType,
            List<Double> queryVector,
            String categoryPathPrefix,
            String levelValue,
            int limit,
            double minScore) {
        throw new UnsupportedOperationException("vectorSearchConcepts is not supported");
    }

}
