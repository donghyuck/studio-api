package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.SearchabilityStatus;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.web.dto.DocumentAutoEvaluationRequestDto;
import studio.one.platform.ai.web.dto.DocumentAutoEvaluationResponseDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

/** Generates revision-bound questions from indexed chunks and immediately evaluates retrieval. */
public final class DocumentAutoEvaluationService {
    private static final int DEFAULT_QUESTION_COUNT = 8;
    private static final int MAX_QUESTION_COUNT = 20;
    private static final int MAX_CANDIDATES = 200;

    private final DocumentUsabilityService usabilityService;
    private final VectorStorePort vectorStorePort;
    private final RagRetrievalEvaluationQuestionSetStore questionSetStore;
    private final RagRetrievalEvaluationRunner evaluationRunner;
    private final DocumentRagEvaluationProjectionService projectionService;

    public DocumentAutoEvaluationService(
            DocumentUsabilityService usabilityService,
            VectorStorePort vectorStorePort,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            RagRetrievalEvaluationRunner evaluationRunner,
            DocumentRagEvaluationProjectionService projectionService) {
        this.usabilityService = java.util.Objects.requireNonNull(usabilityService, "usabilityService");
        this.vectorStorePort = java.util.Objects.requireNonNull(vectorStorePort, "vectorStorePort");
        this.questionSetStore = java.util.Objects.requireNonNull(questionSetStore, "questionSetStore");
        this.evaluationRunner = java.util.Objects.requireNonNull(evaluationRunner, "evaluationRunner");
        this.projectionService = java.util.Objects.requireNonNull(projectionService, "projectionService");
    }

    public DocumentAutoEvaluationResponseDto evaluate(
            String objectType,
            String objectId,
            DocumentAutoEvaluationRequestDto request) {
        DocumentUsabilityAssessment assessment = usabilityService.evaluate(objectType, objectId);
        if (assessment.searchability().status() != SearchabilityStatus.SEARCHABLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Document must be searchable before automatic evaluation");
        }
        int questionCount = requestedQuestionCount(request == null ? null : request.questionCount());
        List<VectorSearchResult> candidates = vectorStorePort.listByObject(objectType, objectId, 0, MAX_CANDIDATES);
        List<RagRetrievalEvaluationRequestDto.Question> questions = questions(
                candidates, assessment.basis().revisionId(), questionCount);
        if (questions.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No revision-compatible chunks are available for question generation");
        }

        Instant now = Instant.now();
        String questionSetId = "auto-reqs-" + UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", DocumentRagEvaluationProjectionService.EVALUATION_KIND);
        metadata.put("generatorVersion", DocumentRagEvaluationProjectionService.GENERATOR_VERSION);
        metadata.put("objectType", objectType);
        metadata.put("objectId", objectId);
        metadata.put("documentId", assessment.basis().documentId());
        metadata.put("revisionId", assessment.basis().revisionId());
        metadata.put("sourceContentHash", assessment.basis().sourceContentHash());
        metadata.put("chunkSetId", assessment.basis().chunkSetId());
        metadata.put("embeddingSpaceId", assessment.basis().embeddingSpaceId());
        metadata.put("policyVersion", assessment.policy().version());
        metadata.put("policyFingerprint", assessment.policy().fingerprint());
        RagRetrievalEvaluationQuestionSetDto questionSet = questionSetStore.save(
                new RagRetrievalEvaluationQuestionSetDto(
                        questionSetId,
                        "자동 문서 평가 " + objectType + "/" + objectId,
                        projectionService.metadataJson(metadata),
                        now,
                        now,
                        questions));

        List<String> requestedStrategies = request == null || request.strategies() == null
                ? List.of()
                : request.strategies().stream().filter(this::hasText).map(String::trim).distinct().toList();
        List<String> strategies = requestedStrategies.isEmpty() ? List.of("hybrid") : requestedStrategies;
        RagRetrievalEvaluationResponseDto result = evaluationRunner.evaluate(
                new RagRetrievalEvaluationRequestDto(
                        strategies,
                        questions,
                        request == null ? 5 : request.topK(),
                        request == null ? 0.0d : request.minScore(),
                        objectType,
                        objectId,
                        questionSetId,
                        null,
                        null,
                        null,
                        null));
        return new DocumentAutoEvaluationResponseDto(
                DocumentUsabilityPolicyResolver.CONTRACT_VERSION,
                new DocumentAutoEvaluationResponseDto.Basis(
                        objectType,
                        objectId,
                        assessment.basis().documentId(),
                        assessment.basis().revisionId(),
                        assessment.basis().sourceContentHash(),
                        assessment.basis().chunkSetId(),
                        assessment.basis().embeddingSpaceId(),
                        List.of("QUESTIONS_BOUND_TO_INDEXED_CHUNKS")),
                DocumentRagEvaluationProjectionService.GENERATOR_VERSION,
                questionSet,
                result);
    }

    private List<RagRetrievalEvaluationRequestDto.Question> questions(
            List<VectorSearchResult> candidates,
            String revisionId,
            int questionCount) {
        List<VectorDocument> compatible = (candidates == null ? List.<VectorSearchResult>of() : candidates).stream()
                .map(VectorSearchResult::document)
                .filter(document -> hasText(document.content()))
                .filter(document -> revisionCompatible(document.metadata(), revisionId))
                .toList();
        if (compatible.isEmpty()) {
            return List.of();
        }
        List<RagRetrievalEvaluationRequestDto.Question> questions = new ArrayList<>();
        Set<String> seenQueries = new LinkedHashSet<>();
        int step = Math.max(1, compatible.size() / Math.min(questionCount, compatible.size()));
        for (int index = 0; index < compatible.size() && questions.size() < questionCount; index += step) {
            VectorDocument document = compatible.get(index);
            String query = query(document);
            if (!seenQueries.add(query)) {
                continue;
            }
            String chunkId = firstText(document.metadata(), "chunkId");
            String documentChunkId = firstText(document.metadata(), "documentChunkId");
            questions.add(new RagRetrievalEvaluationRequestDto.Question(
                    query,
                    chunkId == null ? List.of() : List.of(chunkId),
                    documentChunkId == null ? List.of() : List.of(documentChunkId),
                    List.of(contentNeedle(document.content()))));
        }
        return List.copyOf(questions);
    }

    private String query(VectorDocument document) {
        String title = firstText(document.metadata(), "sectionTitle", "title", "heading");
        if (title != null) {
            return title + "의 핵심 내용은 무엇인가요?";
        }
        return "문서에서 '" + contentNeedle(document.content()) + "'와 관련해 설명하는 내용은 무엇인가요?";
    }

    private String contentNeedle(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        int end = Math.min(normalized.length(), 48);
        return normalized.substring(0, end);
    }

    private boolean revisionCompatible(Map<String, Object> metadata, String revisionId) {
        String candidate = firstText(metadata, "markdownRevisionId", "sourceRevisionId", "revisionId");
        return revisionId == null ? candidate == null : revisionId.equals(candidate);
    }

    private String firstText(Map<String, Object> metadata, String... keys) {
        Map<String, Object> safe = metadata == null ? Map.of() : metadata;
        for (String key : keys) {
            Object value = safe.get(key);
            if (value != null && hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private int requestedQuestionCount(Integer value) {
        return value == null ? DEFAULT_QUESTION_COUNT : Math.max(1, Math.min(MAX_QUESTION_COUNT, value));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
