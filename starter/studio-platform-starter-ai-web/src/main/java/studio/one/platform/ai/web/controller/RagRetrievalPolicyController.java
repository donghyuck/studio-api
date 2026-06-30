package studio.one.platform.ai.web.controller;

import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRecommendationDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyApplyRecommendationRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyHistoryDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageSummaryDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/chat/rag/retrieval-policies")
public class RagRetrievalPolicyController {

    private final RagRetrievalPolicyStore policyStore;
    private final RagRetrievalEvaluationStore evaluationStore;
    private final RagRetrievalEvaluationQuestionSetStore questionSetStore;
    private final RagRetrievalRecommendationService recommendationService;
    private final AiWebRagProperties.RetrievalProperties retrievalProperties;
    private final RagRetrievalPolicyUsageStore usageStore;
    private final RagRetrievalPolicyHistoryStore historyStore;

    public RagRetrievalPolicyController(
            RagRetrievalPolicyStore policyStore,
            RagRetrievalEvaluationStore evaluationStore,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            RagRetrievalRecommendationService recommendationService) {
        this(policyStore, evaluationStore, questionSetStore, recommendationService,
                new AiWebRagProperties.RetrievalProperties(),
                new InMemoryRagRetrievalPolicyUsageStore(),
                new InMemoryRagRetrievalPolicyHistoryStore());
    }

    public RagRetrievalPolicyController(
            RagRetrievalPolicyStore policyStore,
            RagRetrievalEvaluationStore evaluationStore,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            RagRetrievalRecommendationService recommendationService,
            AiWebRagProperties.RetrievalProperties retrievalProperties,
            RagRetrievalPolicyUsageStore usageStore,
            RagRetrievalPolicyHistoryStore historyStore) {
        this.policyStore = Objects.requireNonNull(policyStore, "policyStore");
        this.evaluationStore = Objects.requireNonNull(evaluationStore, "evaluationStore");
        this.questionSetStore = Objects.requireNonNull(questionSetStore, "questionSetStore");
        this.recommendationService = Objects.requireNonNull(recommendationService, "recommendationService");
        this.retrievalProperties = retrievalProperties == null
                ? new AiWebRagProperties.RetrievalProperties()
                : retrievalProperties;
        this.usageStore = Objects.requireNonNull(usageStore, "usageStore");
        this.historyStore = Objects.requireNonNull(historyStore, "historyStore");
    }

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<List<RagRetrievalPolicyDto>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(policyStore.list()));
    }

    @GetMapping("/{objectType}/{objectId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalPolicyDto>> get(
            @PathVariable String objectType,
            @PathVariable String objectId) {
        return policyStore.find(objectType, objectId)
                .map(policy -> ResponseEntity.ok(ApiResponse.ok(policy)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/{objectType}/{objectId}/usage")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<List<RagRetrievalPolicyUsageDto>>> usage(
            @PathVariable String objectType,
            @PathVariable String objectId) {
        return ResponseEntity.ok(ApiResponse.ok(usageStore.list(objectType, objectId)));
    }

    @GetMapping("/{objectType}/{objectId}/usage/summary")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalPolicyUsageSummaryDto>> usageSummary(
            @PathVariable String objectType,
            @PathVariable String objectId) {
        return ResponseEntity.ok(ApiResponse.ok(usageStore.summary(objectType, objectId)));
    }

    @GetMapping("/{objectType}/{objectId}/history")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<List<RagRetrievalPolicyHistoryDto>>> history(
            @PathVariable String objectType,
            @PathVariable String objectId) {
        return ResponseEntity.ok(ApiResponse.ok(historyStore.list(objectType, objectId)));
    }

    @PutMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','write')")
    public ResponseEntity<ApiResponse<RagRetrievalPolicyDto>> save(
            @Valid @RequestBody RagRetrievalPolicyRequestDto request) {
        RagRetrievalPolicyDto saved = policyStore.save(toPolicy(request));
        recordHistory(saved, "MANUAL_SAVE");
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PostMapping("/apply-recommendation")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','write')")
    public ResponseEntity<ApiResponse<RagRetrievalPolicyDto>> applyRecommendation(
            @Valid @RequestBody RagRetrievalPolicyApplyRecommendationRequestDto request) {
        if (questionSetStore.find(request.questionSetId()).isEmpty()) {
            throw new RagRetrievalPolicyException(
                    HttpStatus.NOT_FOUND,
                    "RETRIEVAL_QUESTION_SET_NOT_FOUND",
                    "Retrieval evaluation question set was not found: " + request.questionSetId());
        }
        String objectType = request.objectType().trim();
        String objectId = request.objectId().trim();
        List<RagRetrievalEvaluationResponseDto> objectRuns =
                evaluationStore.listByQuestionSet(request.questionSetId()).stream()
                        .filter(run -> objectType.equals(run.objectType()) && objectId.equals(run.objectId()))
                        .toList();
        RagRetrievalEvaluationRecommendationDto recommendation = recommendationService.recommend(
                request.questionSetId(),
                objectRuns);
        if (recommendation.recommendedStrategy() == null || recommendation.strategies().isEmpty()) {
            throw new RagRetrievalPolicyException(
                    HttpStatus.CONFLICT,
                    "NO_EVALUATION_RUNS_FOR_QUESTION_SET",
                    "No retrieval evaluation runs were found for the question set and object: questionSetId=%s, objectType=%s, objectId=%s"
                            .formatted(request.questionSetId(), objectType, objectId));
        }
        RagRetrievalEvaluationRecommendationDto.StrategyScore best = recommendation.strategies().get(0);
        if (best.hitRate() < retrievalProperties.getMinRecommendationHitRate()
                || best.mrr() < retrievalProperties.getMinRecommendationMrr()) {
            throw new RagRetrievalPolicyException(
                    HttpStatus.CONFLICT,
                    "RETRIEVAL_RECOMMENDATION_QUALITY_TOO_LOW",
                    "Recommended retrieval strategy quality is below threshold: strategy=%s, hitRate=%.4f, mrr=%.4f"
                            .formatted(best.strategy(), best.hitRate(), best.mrr()));
        }
        String evaluationRunId = best.runIds() == null || best.runIds().isEmpty() ? null : best.runIds().get(0);
        RagRetrievalPolicyDto policy = new RagRetrievalPolicyDto(
                objectType,
                objectId,
                recommendation.recommendedStrategy(),
                defaultRetrievalOptions(),
                request.questionSetId().trim(),
                evaluationRunId,
                best.score(),
                best.hitRate(),
                best.mrr(),
                best.averageElapsedMs(),
                Instant.now(),
                Instant.now());
        RagRetrievalPolicyDto saved = policyStore.save(policy);
        recordHistory(saved, "APPLY_RECOMMENDATION");
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    private RagRetrievalPolicyDto toPolicy(RagRetrievalPolicyRequestDto request) {
        Instant now = Instant.now();
        return new RagRetrievalPolicyDto(
                request.objectType().trim(),
                request.objectId().trim(),
                request.retrievalStrategy().trim(),
                request.retrievalOptions(),
                trim(request.questionSetId()),
                trim(request.evaluationRunId()),
                request.score(),
                request.hitRate(),
                request.mrr(),
                request.averageElapsedMs(),
                now,
                now);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ChatRagRetrievalOptionsDto defaultRetrievalOptions() {
        return new ChatRagRetrievalOptionsDto(
                retrievalProperties.getStructureTopK(),
                retrievalProperties.getIdeaBlockTopK(),
                retrievalProperties.getFinalTopK(),
                null,
                retrievalProperties.isDedupe(),
                null,
                retrievalProperties.getDistilledScoreBoost(),
                null);
    }

    private void recordHistory(RagRetrievalPolicyDto policy, String reason) {
        historyStore.save(new RagRetrievalPolicyHistoryDto(
                "rph-" + UUID.randomUUID(),
                policy.objectType(),
                policy.objectId(),
                policy.retrievalStrategy(),
                reason,
                policy.questionSetId(),
                policy.evaluationRunId(),
                policy.score(),
                policy.hitRate(),
                policy.mrr(),
                policy.averageElapsedMs(),
                Instant.now()));
    }
}
