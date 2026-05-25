package studio.one.platform.skillgraph.application.service;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import studio.one.platform.skillgraph.application.command.SkillReferenceEmbeddingCommand;
import studio.one.platform.skillgraph.application.command.SkillReferenceVectorSearchCommand;
import studio.one.platform.skillgraph.application.result.SkillReferenceEmbeddingResult;
import studio.one.platform.skillgraph.application.result.SkillReferenceConceptView;
import studio.one.platform.skillgraph.application.result.SkillReferenceDatasetView;
import studio.one.platform.skillgraph.application.result.SkillReferenceRelationView;
import studio.one.platform.skillgraph.application.result.SkillReferenceRoadmapContextView;
import studio.one.platform.skillgraph.application.result.SkillReferenceVectorSearchResult;
import studio.one.platform.skillgraph.application.usecase.SkillReferenceDatasetService;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConcept;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConceptEmbedding;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetStore;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillRelation;
import studio.one.platform.skillgraph.infrastructure.skilldataset.ncs.NcsTypes;

public class DefaultSkillReferenceDatasetService implements SkillReferenceDatasetService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int ROADMAP_RELATION_LIMIT = 500;
    private static final int CONCEPT_EMBEDDING_DIMENSION = 1024;

    private final SkillDatasetStore datasetStore;
    private final SkillEmbeddingPort embeddingPort;
    private final SkillReferenceEmbeddingTextBuilder textBuilder = new SkillReferenceEmbeddingTextBuilder();

    public DefaultSkillReferenceDatasetService(SkillDatasetStore datasetStore) {
        this(datasetStore, new NoOpSkillEmbeddingPort());
    }

    public DefaultSkillReferenceDatasetService(SkillDatasetStore datasetStore, SkillEmbeddingPort embeddingPort) {
        this.datasetStore = datasetStore;
        this.embeddingPort = embeddingPort == null ? new NoOpSkillEmbeddingPort() : embeddingPort;
    }

    @Override
    public Page<SkillReferenceDatasetView> listDatasets(Pageable pageable) {
        return datasetStore.findDatasets(pageable)
                .map(SkillReferenceDatasetView::from);
    }

    @Override
    public List<SkillReferenceConceptView> listConcepts(String datasetId, String conceptType, String query, int limit) {
        requireDatasetId(datasetId);
        if (query != null && !query.isBlank()) {
            return search(datasetId, conceptType, query, limit);
        }
        return datasetStore.findConcepts(datasetId, blankToNull(conceptType), normalizeLimit(limit)).stream()
                .map(SkillReferenceConceptView::from)
                .toList();
    }

    @Override
    public Page<SkillReferenceConceptView> listConcepts(
            String datasetId,
            String conceptType,
            String query,
            Pageable pageable) {
        requireDatasetId(datasetId);
        if (query != null && !query.isBlank()) {
            return search(datasetId, conceptType, query, pageable);
        }
        return datasetStore.findConcepts(datasetId, blankToNull(conceptType), pageable)
                .map(SkillReferenceConceptView::from);
    }

    @Override
    public SkillReferenceConceptView getConcept(String datasetId, String conceptId) {
        return SkillReferenceConceptView.from(findConcept(datasetId, conceptId));
    }

    @Override
    public List<SkillReferenceConceptView> listChildren(String datasetId, String conceptId, String relationType, int limit) {
        requireDatasetId(datasetId);
        requireConceptId(conceptId);
        return datasetStore.findChildConcepts(datasetId, conceptId, blankToNull(relationType), normalizeLimit(limit)).stream()
                .map(SkillReferenceConceptView::from)
                .toList();
    }

    @Override
    public Page<SkillReferenceConceptView> listChildren(
            String datasetId,
            String conceptId,
            String relationType,
            Pageable pageable) {
        requireDatasetId(datasetId);
        requireConceptId(conceptId);
        return datasetStore.findChildConcepts(datasetId, conceptId, blankToNull(relationType), pageable)
                .map(SkillReferenceConceptView::from);
    }

    @Override
    public List<SkillReferenceConceptView> search(String datasetId, String conceptType, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return datasetStore.searchConcepts(blankToNull(datasetId), blankToNull(conceptType), query, normalizeLimit(limit)).stream()
                .map(SkillReferenceConceptView::from)
                .toList();
    }

    @Override
    public Page<SkillReferenceConceptView> search(String datasetId, String conceptType, String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return Page.empty(pageable);
        }
        return datasetStore.searchConcepts(blankToNull(datasetId), blankToNull(conceptType), query, pageable)
                .map(SkillReferenceConceptView::from);
    }

    @Override
    public SkillReferenceEmbeddingResult embedConcepts(SkillReferenceEmbeddingCommand command) {
        requireDatasetId(command.datasetId());
        String embeddingProvider = requireText(command.embeddingProvider(), "embeddingProvider");
        String embeddingModel = requireText(command.embeddingModel(), "embeddingModel");
        String textType = requireText(command.textType(), "textType");
        int batchSize = command.batchSize() <= 0 ? 20 : Math.min(command.batchSize(), 100);
        int requestedDimension = command.embeddingDim() <= 0
                ? CONCEPT_EMBEDDING_DIMENSION
                : command.embeddingDim();
        if (requestedDimension != CONCEPT_EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("skill reference concept embedding currently supports only "
                    + CONCEPT_EMBEDDING_DIMENSION + " dimensions");
        }
        long total = datasetStore.countConcepts(
                command.datasetId(),
                blankToNull(command.provider()),
                blankToNull(command.conceptType()));
        long processed = 0;
        long embedded = 0;
        long skipped = 0;
        long failed = 0;

        for (long offset = 0; offset < total; offset += batchSize) {
            List<SkillConcept> concepts = datasetStore.findConceptsForEmbedding(
                    command.datasetId(),
                    blankToNull(command.provider()),
                    blankToNull(command.conceptType()),
                    batchSize,
                    offset);
            List<EmbeddingWork> works = new ArrayList<>();
            for (SkillConcept concept : concepts) {
                processed++;
                try {
                    String sourceText = textBuilder.build(concept, command.textBuildStrategy());
                    if (sourceText.isBlank()) {
                        skipped++;
                        continue;
                    }
                    String sourceHash = sha256(sourceText);
                    if (!command.overwrite() && datasetStore.conceptEmbeddingExists(
                            concept.conceptId(), embeddingProvider, embeddingModel, textType, sourceHash)) {
                        skipped++;
                        continue;
                    }
                    works.add(new EmbeddingWork(concept, sourceText, sourceHash));
                } catch (RuntimeException ex) {
                    failed++;
                }
            }
            if (works.isEmpty()) {
                continue;
            }
            List<List<Double>> vectors;
            try {
                vectors = embeddingPort.embedSkills(
                        works.stream().map(EmbeddingWork::sourceText).toList(),
                        embeddingProvider,
                        embeddingModel);
            } catch (RuntimeException ex) {
                failed += works.size();
                continue;
            }
            for (int index = 0; index < works.size(); index++) {
                EmbeddingWork work = works.get(index);
                try {
                    List<Double> vector = index < vectors.size() ? vectors.get(index) : List.of();
                    if (vector == null || vector.isEmpty()) {
                        failed++;
                        continue;
                    }
                    vector = command.normalize() ? normalize(vector) : vector;
                    if (vector.size() != CONCEPT_EMBEDDING_DIMENSION) {
                        throw new IllegalStateException("Embedding dimension " + vector.size()
                                + " does not match required dimension " + CONCEPT_EMBEDDING_DIMENSION);
                    }
                    SkillConcept concept = work.concept();
                    if (command.overwrite()) {
                        datasetStore.deleteConceptEmbedding(concept.conceptId(), embeddingProvider, embeddingModel, textType);
                    }
                    datasetStore.upsertConceptEmbedding(new SkillConceptEmbedding(
                            "sce-" + UUID.randomUUID(),
                            concept.conceptId(),
                            concept.datasetId(),
                            concept.provider(),
                            concept.conceptType(),
                            concept.externalCode(),
                            concept.preferredLabel(),
                            embeddingProvider,
                            embeddingModel,
                            vector.size(),
                            textType,
                            work.sourceText(),
                            work.sourceHash(),
                            vector));
                    embedded++;
                } catch (RuntimeException ex) {
                    failed++;
                }
            }
        }
        return new SkillReferenceEmbeddingResult(
                command.datasetId(),
                command.conceptType(),
                embeddingProvider,
                embeddingModel,
                textType,
                total,
                processed,
                embedded,
                skipped,
                failed);
    }

    private record EmbeddingWork(SkillConcept concept, String sourceText, String sourceHash) {
    }

    @Override
    public List<SkillReferenceVectorSearchResult> vectorSearch(SkillReferenceVectorSearchCommand command) {
        requireDatasetId(command.datasetId());
        String query = requireText(command.query(), "query");
        String embeddingProvider = requireText(command.embeddingProvider(), "embeddingProvider");
        String embeddingModel = requireText(command.embeddingModel(), "embeddingModel");
        String textType = requireText(command.textType(), "textType");
        List<Double> queryVector = embeddingPort.embedSkill(
                query,
                command.embeddingProvider(),
                command.embeddingModel());
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        queryVector = command.normalize() ? normalize(queryVector) : queryVector;
        return datasetStore.vectorSearchConcepts(
                        command.datasetId(),
                        blankToNull(command.provider()),
                        blankToNull(command.conceptType()),
                        embeddingProvider,
                        embeddingModel,
                        textType,
                        queryVector,
                        blankToNull(command.categoryPathPrefix()),
                        blankToNull(command.levelValue()),
                        normalizeLimit(command.topK()),
                        command.minScore() <= 0 ? 0 : command.minScore())
                .stream()
                .map(hit -> new SkillReferenceVectorSearchResult(
                        SkillReferenceConceptView.from(hit.concept()),
                        hit.embeddingId(),
                        hit.embeddingProvider(),
                        hit.embeddingModel(),
                        hit.textType(),
                        hit.score(),
                        hit.sourceText()))
                .toList();
    }

    @Override
    public SkillReferenceRoadmapContextView roadmapContext(String datasetId, String conceptId) {
        SkillConcept unit = findConcept(datasetId, conceptId);
        if (!NcsTypes.COMPETENCY_UNIT.equals(unit.conceptType())) {
            throw new IllegalArgumentException("roadmap context requires NCS competency unit concept: " + conceptId);
        }

        List<SkillRelation> unitRelations = datasetStore.findOutgoingRelations(
                datasetId, conceptId, null, ROADMAP_RELATION_LIMIT);
        List<SkillReferenceConceptView> elements = targetConcepts(datasetId, unitRelations, NcsTypes.HAS_COMPETENCY_ELEMENT);
        List<SkillReferenceConceptView> ksa = unitRelations.stream()
                .filter(relation -> relation.relationType().startsWith("REQUIRES_"))
                .map(SkillRelation::targetConceptId)
                .distinct()
                .map(targetId -> datasetStore.findConcept(datasetId, targetId).orElse(null))
                .filter(concept -> concept != null)
                .map(SkillReferenceConceptView::from)
                .toList();

        List<SkillRelation> elementRelations = new ArrayList<>();
        for (SkillReferenceConceptView element : elements) {
            elementRelations.addAll(datasetStore.findOutgoingRelations(
                    datasetId, element.conceptId(), NcsTypes.HAS_PERFORMANCE_CRITERIA, ROADMAP_RELATION_LIMIT));
        }

        List<SkillReferenceConceptView> criteria = elementRelations.stream()
                .map(SkillRelation::targetConceptId)
                .distinct()
                .map(targetId -> datasetStore.findConcept(datasetId, targetId).orElse(null))
                .filter(concept -> concept != null)
                .map(SkillReferenceConceptView::from)
                .toList();

        List<SkillReferenceRelationView> relations = mergeRelations(unitRelations, elementRelations).stream()
                .map(SkillReferenceRelationView::from)
                .toList();

        return new SkillReferenceRoadmapContextView(
                SkillReferenceConceptView.from(unit),
                elements,
                criteria,
                ksa,
                relations);
    }

    private SkillConcept findConcept(String datasetId, String conceptId) {
        requireDatasetId(datasetId);
        requireConceptId(conceptId);
        return datasetStore.findConcept(datasetId, conceptId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reference concept: " + conceptId));
    }

    private List<SkillReferenceConceptView> targetConcepts(
            String datasetId,
            List<SkillRelation> relations,
            String relationType) {
        List<String> targetIds = relations.stream()
                .filter(relation -> relationType.equals(relation.relationType()))
                .map(SkillRelation::targetConceptId)
                .distinct()
                .toList();
        return datasetStore.findConceptsByIds(datasetId, targetIds).stream()
                .map(SkillReferenceConceptView::from)
                .toList();
    }

    private List<SkillRelation> mergeRelations(List<SkillRelation> first, List<SkillRelation> second) {
        Set<String> relationIds = new LinkedHashSet<>();
        List<SkillRelation> merged = new ArrayList<>();
        for (SkillRelation relation : first) {
            if (relationIds.add(relation.relationId())) {
                merged.add(relation);
            }
        }
        for (SkillRelation relation : second) {
            if (relationIds.add(relation.relationId())) {
                merged.add(relation);
            }
        }
        return merged;
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 1000);
    }

    private void requireDatasetId(String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId must not be blank");
        }
    }

    private void requireConceptId(String conceptId) {
        if (conceptId == null || conceptId.isBlank()) {
            throw new IllegalArgumentException("conceptId must not be blank");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private List<Double> normalize(List<Double> vector) {
        double sum = 0;
        for (Double value : vector) {
            if (value != null) {
                sum += value * value;
            }
        }
        if (sum == 0) {
            return vector;
        }
        double norm = Math.sqrt(sum);
        return vector.stream()
                .map(value -> value == null ? 0d : value / norm)
                .toList();
    }
}
