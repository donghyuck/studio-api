package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationCompareRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationCompareResponseDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationAnalysisDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationBenchmarkReportDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationBenchmarkRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationJobDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetRunRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRecommendationDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.ai.endpoints.base-path:/api/ai}/chat/rag/evaluations")
public class RagRetrievalEvaluationController {
    private final RagRetrievalEvaluationRunner evaluationRunner;
    private final RagRetrievalEvaluationStore evaluationStore;
    private final RagRetrievalEvaluationJobService jobService;
    private final RagRetrievalEvaluationQuestionSetStore questionSetStore;
    private final RagRetrievalRecommendationService recommendationService;

    public RagRetrievalEvaluationController(
            RagRetrievalEvaluationRunner evaluationRunner,
            RagRetrievalEvaluationStore evaluationStore,
            RagRetrievalEvaluationJobService jobService,
            RagRetrievalEvaluationQuestionSetStore questionSetStore,
            RagRetrievalRecommendationService recommendationService) {
        this.evaluationRunner = Objects.requireNonNull(evaluationRunner, "evaluationRunner");
        this.evaluationStore = Objects.requireNonNull(evaluationStore, "evaluationStore");
        this.jobService = Objects.requireNonNull(jobService, "jobService");
        this.questionSetStore = Objects.requireNonNull(questionSetStore, "questionSetStore");
        this.recommendationService = Objects.requireNonNull(recommendationService, "recommendationService");
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationResponseDto>> evaluate(
            @Valid @RequestBody RagRetrievalEvaluationRequestDto request) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationRunner.evaluate(request)));
    }

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<List<RagRetrievalEvaluationResponseDto>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(evaluationStore.list()));
    }

    @GetMapping("/{runId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationResponseDto>> get(@PathVariable String runId) {
        return evaluationStore.find(runId)
                .map(result -> ResponseEntity.ok(ApiResponse.ok(result)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping(value = "/{runId}/export.csv", produces = "text/csv")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<String> exportCsv(@PathVariable String runId) {
        return evaluationStore.find(runId)
                .map(result -> ResponseEntity.ok()
                        .contentType(new MediaType("text", "csv"))
                        .body(toCsv(result)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/jobs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationJobDto>> createJob(
            @Valid @RequestBody RagRetrievalEvaluationRequestDto request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(jobService.submit(request)));
    }

    @PostMapping("/question-sets")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationQuestionSetDto>> createQuestionSet(
            @Valid @RequestBody RagRetrievalEvaluationQuestionSetRequestDto request) {
        Instant now = Instant.now();
        RagRetrievalEvaluationQuestionSetDto questionSet = questionSetStore.save(
                new RagRetrievalEvaluationQuestionSetDto(
                        "reqs-" + UUID.randomUUID(),
                        request.name(),
                        request.description(),
                        now,
                        now,
                        List.copyOf(request.questions())));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(questionSet));
    }

    @GetMapping("/question-sets")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<List<RagRetrievalEvaluationQuestionSetDto>>> listQuestionSets() {
        return ResponseEntity.ok(ApiResponse.ok(questionSetStore.list()));
    }

    @GetMapping("/question-sets/{questionSetId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationQuestionSetDto>> getQuestionSet(
            @PathVariable String questionSetId) {
        return questionSetStore.find(questionSetId)
                .map(questionSet -> ResponseEntity.ok(ApiResponse.ok(questionSet)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/question-sets/{questionSetId}/runs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<List<RagRetrievalEvaluationResponseDto>>> listRunsByQuestionSet(
            @PathVariable String questionSetId) {
        if (questionSetStore.find(questionSetId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(ApiResponse.ok(evaluationStore.listByQuestionSet(questionSetId)));
    }

    @GetMapping("/question-sets/{questionSetId}/recommendation")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationRecommendationDto>> recommendByQuestionSet(
            @PathVariable String questionSetId) {
        if (questionSetStore.find(questionSetId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(ApiResponse.ok(recommendationService.recommend(
                questionSetId,
                evaluationStore.listByQuestionSet(questionSetId))));
    }

    @GetMapping("/question-sets/{questionSetId}/analysis")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationAnalysisDto>> analyzeByQuestionSet(
            @PathVariable String questionSetId) {
        if (questionSetStore.find(questionSetId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(ApiResponse.ok(recommendationService.analyze(
                questionSetId,
                evaluationStore.listByQuestionSet(questionSetId))));
    }

    @PostMapping("/question-sets/{questionSetId}/jobs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationJobDto>> createJobFromQuestionSet(
            @PathVariable String questionSetId,
            @Valid @RequestBody RagRetrievalEvaluationQuestionSetRunRequestDto request) {
        return questionSetStore.find(questionSetId)
                .map(questionSet -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(ApiResponse.ok(jobService.submit(new RagRetrievalEvaluationRequestDto(
                                request.strategies(),
                                questionSet.questions(),
                                request.topK(),
                                request.minScore(),
                                request.objectType(),
                                request.objectId(),
                                questionSet.questionSetId(),
                                request.embeddingProfileId(),
                                request.embeddingProvider(),
                                request.embeddingModel(),
                                request.retrievalOptions())))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/question-sets/{questionSetId}/benchmark")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationBenchmarkReportDto>> runBenchmark(
            @PathVariable String questionSetId,
            @Valid @RequestBody RagRetrievalEvaluationBenchmarkRequestDto request) {
        return questionSetStore.find(questionSetId)
                .map(questionSet -> ResponseEntity.ok(ApiResponse.ok(runBenchmark(questionSet, request))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/jobs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<List<RagRetrievalEvaluationJobDto>>> listJobs() {
        return ResponseEntity.ok(ApiResponse.ok(jobService.list()));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationJobDto>> getJob(@PathVariable String jobId) {
        return jobService.find(jobId)
                .map(job -> ResponseEntity.ok(ApiResponse.ok(job)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/compare")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagRetrievalEvaluationCompareResponseDto>> compare(
            @Valid @RequestBody RagRetrievalEvaluationCompareRequestDto request) {
        RagRetrievalEvaluationResponseDto before = evaluationStore.find(request.beforeRunId())
                .orElse(null);
        RagRetrievalEvaluationResponseDto after = evaluationStore.find(request.afterRunId())
                .orElse(null);
        if (before == null || after == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(ApiResponse.ok(evaluationRunner.compare(before, after)));
    }

    private String toCsv(RagRetrievalEvaluationResponseDto result) {
        StringBuilder csv = new StringBuilder();
        csv.append("runId,strategy,query,hit,firstRelevantRank,elapsedMs,rank,chunkId,documentChunkId,documentId,score,metadataPreview\n");
        for (RagRetrievalEvaluationResponseDto.StrategyResult strategy : result.strategies()) {
            for (RagRetrievalEvaluationResponseDto.QuestionResult question : strategy.questions()) {
                if (question.results() == null || question.results().isEmpty()) {
                    appendRow(csv, result.runId(), strategy.strategy(), question.query(), question.hit(),
                            question.firstRelevantRank(), question.elapsedMs(), null, null, null, null, null, null);
                    continue;
                }
                for (RagRetrievalEvaluationResponseDto.ResultItem item : question.results()) {
                    appendRow(csv, result.runId(), strategy.strategy(), question.query(), question.hit(),
                            question.firstRelevantRank(), question.elapsedMs(), item.rank(), item.chunkId(),
                            item.documentChunkId(), item.documentId(), item.score(), item.metadataPreview());
                }
            }
        }
        return csv.toString();
    }

    private RagRetrievalEvaluationBenchmarkReportDto runBenchmark(
            RagRetrievalEvaluationQuestionSetDto questionSet,
            RagRetrievalEvaluationBenchmarkRequestDto request) {
        int repetitions = request.repetitions() == null ? 1 : request.repetitions();
        List<RagRetrievalEvaluationResponseDto> allRuns = new ArrayList<>();
        List<RagRetrievalEvaluationBenchmarkReportDto.ObjectReport> objectReports = new ArrayList<>();
        for (RagRetrievalEvaluationBenchmarkRequestDto.ObjectScope object : request.objects()) {
            List<RagRetrievalEvaluationResponseDto> objectRuns = new ArrayList<>();
            for (int index = 0; index < repetitions; index++) {
                RagRetrievalEvaluationResponseDto run = evaluationRunner.evaluate(new RagRetrievalEvaluationRequestDto(
                        request.strategies(),
                        questionSet.questions(),
                        request.topK(),
                        request.minScore(),
                        object.objectType(),
                        object.objectId(),
                        questionSet.questionSetId(),
                        request.embeddingProfileId(),
                        request.embeddingProvider(),
                        request.embeddingModel(),
                        request.retrievalOptions()));
                objectRuns.add(run);
                allRuns.add(run);
            }
            objectReports.add(new RagRetrievalEvaluationBenchmarkReportDto.ObjectReport(
                    object.objectType(),
                    object.objectId(),
                    object.label(),
                    objectRuns.size(),
                    objectRuns.stream().map(RagRetrievalEvaluationResponseDto::runId).toList(),
                    recommendationService.recommend(questionSet.questionSetId(), objectRuns),
                    recommendationService.analyze(questionSet.questionSetId(), objectRuns)));
        }
        return new RagRetrievalEvaluationBenchmarkReportDto(
                questionSet.questionSetId(),
                Instant.now(),
                request.objects().size(),
                allRuns.size(),
                recommendationService.recommend(questionSet.questionSetId(), allRuns),
                recommendationService.analyze(questionSet.questionSetId(), allRuns),
                objectReports);
    }

    private void appendRow(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(values[index]));
        }
        csv.append('\n');
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
