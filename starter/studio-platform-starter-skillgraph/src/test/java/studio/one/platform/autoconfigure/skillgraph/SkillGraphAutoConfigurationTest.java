package studio.one.platform.autoconfigure.skillgraph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.skillgraph.application.service.DefaultSkillExtractionService;
import studio.one.platform.skillgraph.application.service.SkillDatasetImportJobService;
import studio.one.platform.skillgraph.application.service.SkillDatasetImportJobWorker;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryDraftService;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphService;
import studio.one.platform.skillgraph.application.usecase.SkillMappingService;
import studio.one.platform.skillgraph.application.usecase.SkillRecommendationService;
import studio.one.platform.skillgraph.application.usecase.SkillReferenceDatasetService;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillDatasetImportJobStore;
import studio.one.platform.skillgraph.domain.port.SkillGraphStore;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;
import studio.one.platform.skillgraph.domain.port.SkillRagExtractionJobStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;
import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJob;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConcept;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDataset;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetImporter;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetStore;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillRelation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class SkillGraphAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SkillGraphAutoConfiguration.class));

    @Test
    void createsDefaultMemorySkillGraphBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SkillCandidateExtractor.class);
            assertThat(context).hasSingleBean(SkillCandidateStore.class);
            assertThat(context).hasSingleBean(SkillDictionaryStore.class);
            assertThat(context).hasSingleBean(SkillExtractionService.class);
            assertThat(context).hasSingleBean(SkillCandidateReviewService.class);
            assertThat(context).hasSingleBean(SkillDictionaryService.class);
            assertThat(context).hasSingleBean(SkillCategoryDraftService.class);
            assertThat(context).hasSingleBean(SkillTaxonomyStore.class);
            assertThat(context).hasSingleBean(SkillGraphStore.class);
            assertThat(context).hasSingleBean(SkillMappingStore.class);
            assertThat(context).hasSingleBean(SkillRagExtractionJobStore.class);
            assertThat(context).hasSingleBean(SkillTaxonomyService.class);
            assertThat(context).hasSingleBean(SkillGraphService.class);
            assertThat(context).hasSingleBean(SkillMappingService.class);
            assertThat(context).hasSingleBean(SkillRecommendationService.class);
            assertThat(context).getBean(SkillExtractionService.class)
                    .isInstanceOf(DefaultSkillExtractionService.class);
        });
    }

    @Test
    void createsRagExtractionJobServiceAfterWebRagResolverIsAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SkillGraphAutoConfiguration.class,
                        SkillGraphWebAutoConfiguration.SkillGraphRagExtractionConfig.class))
                .withBean(RagPipelineService.class, FakeRagPipelineService::new)
                .withPropertyValues(
                        "studio.features.skillgraph.web.enabled=true",
                        "studio.skillgraph.extraction.rag-job.batch-size=10",
                        "studio.skillgraph.extraction.rag-job.worker-count=1",
                        "studio.skillgraph.extraction.rag-job.queue-capacity=2",
                        "studio.skillgraph.extraction.rag-job.max-chunks=100",
                        "studio.skillgraph.extraction.rag-job.max-text-bytes-per-batch=20000")
                .run(context -> assertThat(context).hasSingleBean(SkillRagExtractionJobService.class));
    }

    @Test
    void createsLlmSkillExtractionServiceWhenEnabled() {
        contextRunner
                .withPropertyValues("studio.skillgraph.extraction.mode=llm")
                .withBean(PromptRenderer.class, FakePromptRenderer::new)
                .withBean(ChatPort.class, () -> request -> new ChatResponse(
                        List.of(ChatMessage.assistant("[{\"term\":\"Spring Boot\",\"confidence\":0.9}]")),
                        "test",
                        Map.of()))
                .run(context -> assertThat(context).getBean(SkillExtractionService.class)
                        .isInstanceOf(DefaultSkillExtractionService.class));
    }

    @Test
    void failsLlmSkillExtractionServiceWhenAiBeansAreMissing() {
        contextRunner
                .withPropertyValues("studio.skillgraph.extraction.mode=llm")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void backsOffWhenFeatureIsDisabled() {
        contextRunner
                .withPropertyValues("studio.features.skillgraph.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SkillExtractionService.class));
    }

    @Test
    void doesNotCreateDatasetImportBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SkillDatasetImporter.class);
            assertThat(context).doesNotHaveBean(SkillDatasetImportJobWorker.class);
            assertThat(context).doesNotHaveBean(SkillDatasetImportJobService.class);
            assertThat(context).doesNotHaveBean(SkillReferenceDatasetService.class);
        });
    }

    @Test
    void createsDatasetImportBeansWhenEnabledAndStoresAreAvailable() {
        contextRunner
                .withPropertyValues("studio.skillgraph.dataset-import.enabled=true")
                .withBean(SkillDatasetStore.class, FakeSkillDatasetStore::new)
                .withBean(SkillDatasetImportJobStore.class, FakeSkillDatasetImportJobStore::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(SkillReferenceDatasetService.class);
                    assertThat(context).hasSingleBean(SkillDatasetImporter.class);
                    assertThat(context).hasSingleBean(SkillDatasetImportJobWorker.class);
                    assertThat(context).hasSingleBean(SkillDatasetImportJobService.class);
                });
    }

    @Test
    void disablesNcsDatasetImporterWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "studio.skillgraph.dataset-import.enabled=true",
                        "studio.skillgraph.dataset-import.ncs.enabled=false")
                .withBean(SkillDatasetStore.class, FakeSkillDatasetStore::new)
                .withBean(SkillDatasetImportJobStore.class, FakeSkillDatasetImportJobStore::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SkillDatasetImporter.class);
                    assertThat(context).doesNotHaveBean(SkillDatasetImportJobService.class);
                });
    }

    private static final class FakePromptRenderer implements PromptRenderer {

        @Override
        public String render(String name, Map<String, Object> params) {
            return "prompt";
        }

        @Override
        public String getRawPrompt(String name) {
            return "prompt";
        }
    }

    private static final class FakeSkillDatasetImportJobStore implements SkillDatasetImportJobStore {

        private final Map<String, SkillDatasetImportJob> jobs = new ConcurrentHashMap<>();

        @Override
        public SkillDatasetImportJob save(SkillDatasetImportJob job) {
            jobs.put(job.jobId(), job);
            return job;
        }

        @Override
        public Optional<SkillDatasetImportJob> findById(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }

        @Override
        public List<SkillDatasetImportJob> findRecent(int limit) {
            return List.copyOf(jobs.values());
        }
    }

    private static final class FakeSkillDatasetStore implements SkillDatasetStore {

        private final Map<String, SkillDataset> datasets = new ConcurrentHashMap<>();
        private final Map<String, SkillConcept> concepts = new ConcurrentHashMap<>();
        private final Map<String, SkillRelation> relations = new ConcurrentHashMap<>();

        @Override
        public void saveDataset(SkillDataset dataset) {
            datasets.put(dataset.datasetId(), dataset);
        }

        @Override
        public void upsertConcept(SkillConcept concept) {
            concepts.put(concept.conceptId(), concept);
        }

        @Override
        public void upsertRelation(SkillRelation relation) {
            relations.put(relation.relationId(), relation);
        }

        @Override
        public void upsertConcepts(List<SkillConcept> concepts) {
            concepts.forEach(this::upsertConcept);
        }

        @Override
        public void upsertRelations(List<SkillRelation> relations) {
            relations.forEach(this::upsertRelation);
        }

        @Override
        public Optional<SkillDataset> findDataset(String datasetId) {
            return Optional.ofNullable(datasets.get(datasetId));
        }

        @Override
        public Page<SkillDataset> findDatasets(Pageable pageable) {
            List<SkillDataset> found = List.copyOf(datasets.values());
            return new PageImpl<>(found, pageable, found.size());
        }

        @Override
        public Optional<SkillConcept> findConcept(String conceptId) {
            return Optional.ofNullable(concepts.get(conceptId));
        }

        @Override
        public Optional<SkillConcept> findConcept(String datasetId, String conceptId) {
            return Optional.ofNullable(concepts.get(conceptId))
                    .filter(concept -> concept.datasetId().equals(datasetId));
        }

        @Override
        public List<SkillConcept> findConcepts(String datasetId, String conceptType, int limit) {
            return concepts.values().stream()
                    .filter(concept -> concept.datasetId().equals(datasetId))
                    .filter(concept -> conceptType == null || concept.conceptType().equals(conceptType))
                    .limit(limit <= 0 ? 100 : limit)
                    .toList();
        }

        @Override
        public Page<SkillConcept> findConcepts(String datasetId, String conceptType, Pageable pageable) {
            List<SkillConcept> found = findConcepts(datasetId, conceptType, Integer.MAX_VALUE);
            return new PageImpl<>(found, pageable, found.size());
        }

        @Override
        public List<SkillConcept> searchConcepts(String datasetId, String conceptType, String query, int limit) {
            String keyword = query == null ? "" : query.toLowerCase();
            return concepts.values().stream()
                    .filter(concept -> datasetId == null || concept.datasetId().equals(datasetId))
                    .filter(concept -> conceptType == null || concept.conceptType().equals(conceptType))
                    .filter(concept -> concept.preferredLabel().toLowerCase().contains(keyword))
                    .limit(limit <= 0 ? 100 : limit)
                    .toList();
        }

        @Override
        public Page<SkillConcept> searchConcepts(String datasetId, String conceptType, String query, Pageable pageable) {
            List<SkillConcept> found = searchConcepts(datasetId, conceptType, query, Integer.MAX_VALUE);
            return new PageImpl<>(found, pageable, found.size());
        }

        @Override
        public List<SkillConcept> findChildConcepts(String datasetId, String conceptId, String relationType, int limit) {
            return relations.values().stream()
                    .filter(relation -> relation.datasetId().equals(datasetId))
                    .filter(relation -> relation.sourceConceptId().equals(conceptId))
                    .filter(relation -> relationType == null || relation.relationType().equals(relationType))
                    .map(SkillRelation::targetConceptId)
                    .map(concepts::get)
                    .filter(concept -> concept != null)
                    .limit(limit <= 0 ? 100 : limit)
                    .toList();
        }

        @Override
        public Page<SkillConcept> findChildConcepts(String datasetId, String conceptId, String relationType, Pageable pageable) {
            List<SkillConcept> found = findChildConcepts(datasetId, conceptId, relationType, Integer.MAX_VALUE);
            return new PageImpl<>(found, pageable, found.size());
        }

        @Override
        public List<SkillConcept> findConceptsByIds(String datasetId, List<String> conceptIds) {
            return conceptIds.stream()
                    .map(concepts::get)
                    .filter(concept -> concept != null && concept.datasetId().equals(datasetId))
                    .toList();
        }

        @Override
        public List<SkillRelation> findRelations(String datasetId, String relationType, int limit) {
            return relations.values().stream()
                    .filter(relation -> relation.datasetId().equals(datasetId))
                    .filter(relation -> relationType == null || relation.relationType().equals(relationType))
                    .limit(limit <= 0 ? 100 : limit)
                    .toList();
        }

        @Override
        public List<SkillRelation> findOutgoingRelations(String datasetId, String sourceConceptId, String relationType, int limit) {
            return relations.values().stream()
                    .filter(relation -> relation.datasetId().equals(datasetId))
                    .filter(relation -> relation.sourceConceptId().equals(sourceConceptId))
                    .filter(relation -> relationType == null || relation.relationType().equals(relationType))
                    .limit(limit <= 0 ? 100 : limit)
                    .toList();
        }
    }

    private static final class FakeRagPipelineService implements RagPipelineService {

        @Override
        public void index(RagIndexRequest request) {
        }

        @Override
        public List<RagSearchResult> search(RagSearchRequest request) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> searchByObject(RagSearchRequest request, String objectType, String objectId) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
            return List.of(new RagSearchResult("doc-1", "Spring Boot", Map.of("chunkId", "chunk-1"), 1.0d));
        }

        @Override
        public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
            return Optional.empty();
        }
    }
}
