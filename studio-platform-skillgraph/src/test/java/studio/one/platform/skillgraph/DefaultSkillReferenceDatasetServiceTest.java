package studio.one.platform.skillgraph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.result.SkillReferenceRoadmapContextView;
import studio.one.platform.skillgraph.application.command.SkillReferenceEmbeddingCommand;
import studio.one.platform.skillgraph.application.command.SkillReferenceVectorSearchCommand;
import studio.one.platform.skillgraph.application.result.SkillReferenceEmbeddingResult;
import studio.one.platform.skillgraph.application.service.DefaultSkillReferenceDatasetService;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConcept;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConceptEmbedding;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConceptVectorSearchHit;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDataset;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetStore;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillRelation;
import studio.one.platform.skillgraph.infrastructure.skilldataset.ncs.NcsTypes;

class DefaultSkillReferenceDatasetServiceTest {

    private final FakeSkillDatasetStore store = new FakeSkillDatasetStore();
    private final DefaultSkillReferenceDatasetService service = new DefaultSkillReferenceDatasetService(
            store,
            text -> unitVector(text.contains("Spring") ? 0 : 1));

    @Test
    void searchesReferenceConceptsByDatasetAndType() {
        store.upsertConcept(concept("unit-1", NcsTypes.COMPETENCY_UNIT, "Spring API 개발"));
        store.upsertConcept(concept("element-1", NcsTypes.COMPETENCY_ELEMENT, "Spring API 설계"));
        store.upsertConcept(new SkillConcept(
                "other-1", "other", "NCS", NcsTypes.COMPETENCY_UNIT, "2001", null,
                "Spring 운영", null, null, null, "spring 운영", "{}"));

        assertThat(service.search("ncs", NcsTypes.COMPETENCY_UNIT, "Spring", 10))
                .extracting("conceptId")
                .containsExactly("unit-1");
    }

    @Test
    void buildsRoadmapContextFromCompetencyUnitRelations() {
        store.upsertConcept(concept("unit-1", NcsTypes.COMPETENCY_UNIT, "API 개발"));
        store.upsertConcept(concept("element-1", NcsTypes.COMPETENCY_ELEMENT, "요구사항 분석"));
        store.upsertConcept(concept("criteria-1", NcsTypes.PERFORMANCE_CRITERIA, "요구사항을 분석한다"));
        store.upsertConcept(concept("knowledge-1", NcsTypes.KNOWLEDGE, "HTTP 지식"));
        store.upsertRelation(relation("r1", "unit-1", "element-1", NcsTypes.HAS_COMPETENCY_ELEMENT));
        store.upsertRelation(relation("r2", "element-1", "criteria-1", NcsTypes.HAS_PERFORMANCE_CRITERIA));
        store.upsertRelation(relation("r3", "unit-1", "knowledge-1", NcsTypes.REQUIRES_KNOWLEDGE));

        SkillReferenceRoadmapContextView context = service.roadmapContext("ncs", "unit-1");

        assertThat(context.competencyUnit().conceptId()).isEqualTo("unit-1");
        assertThat(context.competencyElements()).extracting("conceptId").containsExactly("element-1");
        assertThat(context.performanceCriteria()).extracting("conceptId").containsExactly("criteria-1");
        assertThat(context.knowledgeSkillsAttitudes()).extracting("conceptId").containsExactly("knowledge-1");
        assertThat(context.relations()).extracting("relationId").containsExactly("r1", "r3", "r2");
    }

    @Test
    void vectorizesReferenceConceptsAndSearchesByVectorSimilarity() {
        store.upsertConcept(concept("unit-1", NcsTypes.COMPETENCY_UNIT, "Spring API 개발"));
        store.upsertConcept(concept("unit-2", NcsTypes.COMPETENCY_UNIT, "회계 처리"));

        SkillReferenceEmbeddingResult result = service.embedConcepts(new SkillReferenceEmbeddingCommand(
                "ncs",
                "NCS",
                NcsTypes.COMPETENCY_UNIT,
                "kure",
                "nlpai-lab/KURE-v1",
                1024,
                "search_text",
                "LABEL_DESCRIPTION_CATEGORY_RAW_KEYWORDS",
                10,
                false,
                true));

        assertThat(result.embeddedCount()).isEqualTo(2);

        assertThat(service.vectorSearch(new SkillReferenceVectorSearchCommand(
                "ncs",
                "NCS",
                NcsTypes.COMPETENCY_UNIT,
                "kure",
                "nlpai-lab/KURE-v1",
                "search_text",
                "Spring 백엔드",
                5,
                0.5,
                null,
                null,
                true)))
                .extracting(resultItem -> resultItem.concept().conceptId())
                .containsExactly("unit-1");
    }

    @Test
    void vectorizesReferenceConceptsWithBatchEmbeddingRequest() {
        FakeSkillDatasetStore batchStore = new FakeSkillDatasetStore();
        batchStore.upsertConcept(concept("unit-1", NcsTypes.COMPETENCY_UNIT, "Spring API 개발"));
        batchStore.upsertConcept(concept("unit-2", NcsTypes.COMPETENCY_UNIT, "회계 처리"));
        CountingSkillEmbeddingPort embeddingPort = new CountingSkillEmbeddingPort();
        DefaultSkillReferenceDatasetService batchService = new DefaultSkillReferenceDatasetService(
                batchStore,
                embeddingPort);

        SkillReferenceEmbeddingResult result = batchService.embedConcepts(new SkillReferenceEmbeddingCommand(
                "ncs",
                "NCS",
                NcsTypes.COMPETENCY_UNIT,
                "kure",
                "nlpai-lab/KURE-v1",
                1024,
                "search_text",
                "LABEL_DESCRIPTION_CATEGORY_RAW_KEYWORDS",
                20,
                false,
                true));

        assertThat(result.embeddedCount()).isEqualTo(2);
        assertThat(embeddingPort.singleRequestCount).isZero();
        assertThat(embeddingPort.batchRequestCount).isEqualTo(1);
        assertThat(embeddingPort.batchSizes).containsExactly(2);
    }

    private SkillConcept concept(String conceptId, String conceptType, String label) {
        return new SkillConcept(
                conceptId,
                "ncs",
                "NCS",
                conceptType,
                conceptId,
                null,
                label,
                null,
                null,
                null,
                label.toLowerCase(),
                "{}");
    }

    private SkillRelation relation(String relationId, String sourceConceptId, String targetConceptId, String relationType) {
        return new SkillRelation(relationId, "ncs", "NCS", sourceConceptId, targetConceptId, relationType, 1.0d, "{}");
    }

    private static final class FakeSkillDatasetStore implements SkillDatasetStore {

        private final Map<String, SkillConcept> concepts = new LinkedHashMap<>();
        private final Map<String, SkillRelation> relations = new LinkedHashMap<>();
        private final List<SkillConceptEmbedding> embeddings = new java.util.ArrayList<>();

        @Override
        public void saveDataset(SkillDataset dataset) {
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
            return Optional.empty();
        }

        @Override
        public Page<SkillDataset> findDatasets(Pageable pageable) {
            return Page.empty(pageable);
        }

        @Override
        public Optional<SkillConcept> findConcept(String conceptId) {
            return Optional.ofNullable(concepts.get(conceptId));
        }

        @Override
        public Optional<SkillConcept> findConcept(String datasetId, String conceptId) {
            return findConcept(conceptId).filter(concept -> concept.datasetId().equals(datasetId));
        }

        @Override
        public List<SkillConcept> findConcepts(String datasetId, String conceptType, int limit) {
            return concepts.values().stream()
                    .filter(concept -> concept.datasetId().equals(datasetId))
                    .filter(concept -> conceptType == null || concept.conceptType().equals(conceptType))
                    .limit(normalizeLimit(limit))
                    .toList();
        }

        @Override
        public Page<SkillConcept> findConcepts(String datasetId, String conceptType, Pageable pageable) {
            List<SkillConcept> found = findConcepts(datasetId, conceptType, Integer.MAX_VALUE);
            return new PageImpl<>(found, pageable, found.size());
        }

        @Override
        public List<SkillConcept> searchConcepts(String datasetId, String conceptType, String query, int limit) {
            String keyword = query.toLowerCase();
            return concepts.values().stream()
                    .filter(concept -> datasetId == null || concept.datasetId().equals(datasetId))
                    .filter(concept -> conceptType == null || concept.conceptType().equals(conceptType))
                    .filter(concept -> concept.normalizedLabel().contains(keyword))
                    .limit(normalizeLimit(limit))
                    .toList();
        }

        @Override
        public Page<SkillConcept> searchConcepts(String datasetId, String conceptType, String query, Pageable pageable) {
            List<SkillConcept> found = searchConcepts(datasetId, conceptType, query, Integer.MAX_VALUE);
            return new PageImpl<>(found, pageable, found.size());
        }

        @Override
        public List<SkillConcept> findChildConcepts(String datasetId, String conceptId, String relationType, int limit) {
            return findOutgoingRelations(datasetId, conceptId, relationType, limit).stream()
                    .map(SkillRelation::targetConceptId)
                    .map(concepts::get)
                    .filter(concept -> concept != null)
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
                    .limit(normalizeLimit(limit))
                    .toList();
        }

        @Override
        public List<SkillRelation> findOutgoingRelations(String datasetId, String sourceConceptId, String relationType, int limit) {
            return relations.values().stream()
                    .filter(relation -> relation.datasetId().equals(datasetId))
                    .filter(relation -> relation.sourceConceptId().equals(sourceConceptId))
                    .filter(relation -> relationType == null || relation.relationType().equals(relationType))
                    .limit(normalizeLimit(limit))
                    .toList();
        }

        @Override
        public long countConcepts(String datasetId, String provider, String conceptType) {
            return findConceptsForEmbedding(datasetId, provider, conceptType, Integer.MAX_VALUE, 0).size();
        }

        @Override
        public List<SkillConcept> findConceptsForEmbedding(String datasetId, String provider, String conceptType, int limit, long offset) {
            return concepts.values().stream()
                    .filter(concept -> concept.datasetId().equals(datasetId))
                    .filter(concept -> provider == null || concept.provider().equals(provider))
                    .filter(concept -> conceptType == null || concept.conceptType().equals(conceptType))
                    .skip(offset)
                    .limit(normalizeLimit(limit))
                    .toList();
        }

        @Override
        public boolean conceptEmbeddingExists(String conceptId, String embeddingProvider, String embeddingModel, String textType, String sourceHash) {
            return embeddings.stream().anyMatch(embedding -> embedding.conceptId().equals(conceptId)
                    && embedding.embeddingProvider().equals(embeddingProvider)
                    && embedding.embeddingModel().equals(embeddingModel)
                    && embedding.textType().equals(textType)
                    && embedding.sourceHash().equals(sourceHash));
        }

        @Override
        public void deleteConceptEmbedding(String conceptId, String embeddingProvider, String embeddingModel, String textType) {
            embeddings.removeIf(embedding -> embedding.conceptId().equals(conceptId)
                    && embedding.embeddingProvider().equals(embeddingProvider)
                    && embedding.embeddingModel().equals(embeddingModel)
                    && embedding.textType().equals(textType));
        }

        @Override
        public void upsertConceptEmbedding(SkillConceptEmbedding embedding) {
            embeddings.add(embedding);
        }

        @Override
        public List<SkillConceptVectorSearchHit> vectorSearchConcepts(
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
            return embeddings.stream()
                    .filter(embedding -> embedding.datasetId().equals(datasetId))
                    .filter(embedding -> provider == null || embedding.provider().equals(provider))
                    .filter(embedding -> conceptType == null || embedding.conceptType().equals(conceptType))
                    .filter(embedding -> embedding.embeddingProvider().equals(embeddingProvider))
                    .filter(embedding -> embedding.embeddingModel().equals(embeddingModel))
                    .filter(embedding -> embedding.textType().equals(textType))
                    .map(embedding -> new SkillConceptVectorSearchHit(
                            concepts.get(embedding.conceptId()),
                            embedding.embeddingId(),
                            embedding.embeddingProvider(),
                            embedding.embeddingModel(),
                            embedding.textType(),
                            cosine(queryVector, embedding.embedding()),
                            embedding.sourceText()))
                    .filter(hit -> hit.score() >= minScore)
                    .sorted((left, right) -> Double.compare(right.score(), left.score()))
                    .limit(normalizeLimit(limit))
                    .toList();
        }

        private double cosine(List<Double> left, List<Double> right) {
            double dot = 0;
            double leftNorm = 0;
            double rightNorm = 0;
            for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
                dot += left.get(i) * right.get(i);
                leftNorm += left.get(i) * left.get(i);
                rightNorm += right.get(i) * right.get(i);
            }
            if (leftNorm == 0 || rightNorm == 0) {
                return 0;
            }
            return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        }

        private int normalizeLimit(int limit) {
            return limit <= 0 ? 100 : limit;
        }
    }

    private static List<Double> unitVector(int activeIndex) {
        List<Double> vector = new java.util.ArrayList<>(java.util.Collections.nCopies(1024, 0d));
        vector.set(activeIndex, 1d);
        return vector;
    }

    private static final class CountingSkillEmbeddingPort implements SkillEmbeddingPort {

        private int singleRequestCount;
        private int batchRequestCount;
        private final List<Integer> batchSizes = new java.util.ArrayList<>();

        @Override
        public List<Double> embedSkill(String text) {
            singleRequestCount++;
            return unitVector(text.contains("Spring") ? 0 : 1);
        }

        @Override
        public List<List<Double>> embedSkills(List<String> texts, String provider, String model) {
            batchRequestCount++;
            batchSizes.add(texts.size());
            return texts.stream()
                    .map(text -> unitVector(text.contains("Spring") ? 0 : 1))
                    .toList();
        }
    }
}
