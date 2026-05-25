package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.SkillReferenceEmbeddingCommand;
import studio.one.platform.skillgraph.application.command.SkillReferenceVectorSearchCommand;
import studio.one.platform.skillgraph.application.result.SkillReferenceEmbeddingResult;
import studio.one.platform.skillgraph.application.result.SkillReferenceConceptView;
import studio.one.platform.skillgraph.application.result.SkillReferenceDatasetView;
import studio.one.platform.skillgraph.application.result.SkillReferenceRoadmapContextView;
import studio.one.platform.skillgraph.application.result.SkillReferenceVectorSearchResult;

public interface SkillReferenceDatasetService {

    String SERVICE_NAME = "components:skill-reference-dataset-service";

    Page<SkillReferenceDatasetView> listDatasets(Pageable pageable);

    List<SkillReferenceConceptView> listConcepts(String datasetId, String conceptType, String query, int limit);

    Page<SkillReferenceConceptView> listConcepts(String datasetId, String conceptType, String query, Pageable pageable);

    SkillReferenceConceptView getConcept(String datasetId, String conceptId);

    List<SkillReferenceConceptView> listChildren(String datasetId, String conceptId, String relationType, int limit);

    Page<SkillReferenceConceptView> listChildren(String datasetId, String conceptId, String relationType, Pageable pageable);

    List<SkillReferenceConceptView> search(String datasetId, String conceptType, String query, int limit);

    Page<SkillReferenceConceptView> search(String datasetId, String conceptType, String query, Pageable pageable);

    SkillReferenceEmbeddingResult embedConcepts(SkillReferenceEmbeddingCommand command);

    List<SkillReferenceVectorSearchResult> vectorSearch(SkillReferenceVectorSearchCommand command);

    SkillReferenceRoadmapContextView roadmapContext(String datasetId, String conceptId);
}
