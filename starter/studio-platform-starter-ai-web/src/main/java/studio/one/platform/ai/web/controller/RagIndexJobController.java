package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.RagIndexChunkDto;
import studio.one.platform.ai.web.dto.RagIndexJobCreateRequestDto;
import studio.one.platform.ai.web.dto.RagIndexJobDto;
import studio.one.platform.ai.web.dto.RagIndexJobListResponseDto;
import studio.one.platform.ai.web.dto.RagIndexJobLogDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag")
@Validated
public class RagIndexJobController {

    private static final Logger log = LoggerFactory.getLogger(RagIndexJobController.class);
    private static final int DEFAULT_CHUNK_LIMIT = 200;

    private final RagIndexJobService jobService;
    private final RagPipelineService ragPipelineService;
    private final Executor jobExecutor;
    @Nullable
    private final VectorStorePort vectorStorePort;

    public RagIndexJobController(
            RagIndexJobService jobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort) {
        this(jobService, ragPipelineService, vectorStorePort, Runnable::run);
    }

    public RagIndexJobController(
            RagIndexJobService jobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort,
            Executor jobExecutor) {
        this.jobService = Objects.requireNonNull(jobService, "jobService");
        this.ragPipelineService = Objects.requireNonNull(ragPipelineService, "ragPipelineService");
        this.vectorStorePort = vectorStorePort;
        this.jobExecutor = Objects.requireNonNull(jobExecutor, "jobExecutor");
    }

    @GetMapping("/jobs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagIndexJobListResponseDto>> listJobs(
            @RequestParam(name = "status", required = false) RagIndexJobStatus status,
            @RequestParam(name = "objectType", required = false) String objectType,
            @RequestParam(name = "objectId", required = false) String objectId,
            @RequestParam(name = "documentId", required = false) String documentId,
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", required = false) String direction) {
        RagIndexJobPage page = jobService.listJobs(
                new RagIndexJobFilter(status, objectType, objectId, documentId),
                new RagIndexJobPageRequest(
                        offset,
                        limit,
                        RagIndexJobPageRequest.Sort.from(sort),
                        RagIndexJobPageRequest.Direction.from(direction)));
        return ResponseEntity.ok(ApiResponse.ok(new RagIndexJobListResponseDto(
                page.jobs().stream().map(RagIndexJobDto::from).toList(),
                page.total(),
                page.offset(),
                page.limit())));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagIndexJobDto>> getJob(@PathVariable("jobId") String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(RagIndexJobDto.from(requireJob(jobId))));
    }

    @PostMapping("/jobs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentSource(#request)"
            + " or @endpointAuthz.can('features:attachment','write'))")
    public ResponseEntity<ApiResponse<RagIndexJobDto>> createJob(
            @Valid @RequestBody RagIndexJobCreateRequestDto request) {
        CreateJobCommand command = toCreateRequest(request);
        RagIndexJob job = command.sourceRequest() == null
                ? jobService.createJob(command.request())
                : jobService.createJob(command.request(), command.sourceRequest());
        dispatch(job.jobId(), () -> jobService.startJob(job.jobId()));
        return ResponseEntity.accepted().body(ApiResponse.ok(RagIndexJobDto.from(job)));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentJob(#jobId)"
            + " or @endpointAuthz.can('features:attachment','write'))")
    public ResponseEntity<ApiResponse<RagIndexJobDto>> retryJob(@PathVariable("jobId") String jobId) {
        RagIndexJob job = requireJob(jobId);
        if (job.status() == RagIndexJobStatus.PENDING || job.status() == RagIndexJobStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RAG index job is still active");
        }
        dispatch(jobId, () -> jobService.retryJob(jobId));
        return ResponseEntity.accepted().body(ApiResponse.ok(RagIndexJobDto.from(requireJob(jobId))));
    }

    @GetMapping("/jobs/{jobId}/logs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentJob(#jobId)"
            + " or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<List<RagIndexJobLogDto>>> getLogs(@PathVariable("jobId") String jobId) {
        requireJob(jobId);
        return ResponseEntity.ok(ApiResponse.ok(jobService.getLogs(jobId).stream()
                .map(RagIndexJobLogDto::from)
                .toList()));
    }

    @GetMapping("/jobs/{jobId}/chunks")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentJob(#jobId)"
            + " or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<List<RagIndexChunkDto>>> getJobChunks(
            @PathVariable("jobId") String jobId,
            @RequestParam(name = "limit", required = false, defaultValue = "200") int limit) {
        RagIndexJob job = requireJob(jobId);
        if (job.objectType() == null || job.objectId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job has no object scope");
        }
        return objectChunks(job.objectType(), job.objectId(), limit);
    }

    @GetMapping("/objects/{objectType}/{objectId}/chunks")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentObject(#objectType)"
            + " or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<List<RagIndexChunkDto>>> objectChunks(
            @PathVariable("objectType") String objectType,
            @PathVariable("objectId") String objectId,
            @RequestParam(name = "limit", required = false, defaultValue = "200") int limit) {
        int boundedLimit = new RagIndexJobPageRequest(0, limit <= 0 ? DEFAULT_CHUNK_LIMIT : limit).limit();
        List<RagIndexChunkDto> chunks = ragPipelineService.listByObject(objectType, objectId, boundedLimit).stream()
                .map(this::toChunkDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(chunks));
    }

    @GetMapping("/objects/{objectType}/{objectId}/metadata")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentObject(#objectType)"
            + " or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<Map<String, Object>>> objectMetadata(
            @PathVariable("objectType") String objectType,
            @PathVariable("objectId") String objectId) {
        if (vectorStorePort == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VectorStorePort is not configured");
        }
        return ResponseEntity.ok(ApiResponse.ok(vectorStorePort.getMetadata(objectType, objectId)));
    }

    private CreateJobCommand toCreateRequest(RagIndexJobCreateRequestDto request) {
        if (!hasText(request.text()) && !hasText(request.sourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text or sourceType is required for /rag/jobs");
        }
        Map<String, Object> metadata = request.metadata() == null
                ? new HashMap<>()
                : new HashMap<>(request.metadata());
        metadata.put("objectType", request.objectType());
        metadata.put("objectId", request.objectId());
        if (hasText(request.sourceType())) {
            metadata.put("sourceType", request.sourceType().trim());
        }
        String documentId = hasText(request.documentId()) ? request.documentId().trim() : null;
        if (isAttachmentSource(request) && documentId == null) {
            documentId = request.objectId().trim();
            metadata.putIfAbsent("attachmentId", request.objectId().trim());
        }
        RagIndexRequest indexRequest = null;
        if (hasText(request.text())) {
            indexRequest = new RagIndexRequest(
                    documentId == null ? request.objectId().trim() : documentId,
                    request.text(),
                    metadata,
                    request.keywords() == null ? List.of() : request.keywords(),
                    Boolean.TRUE.equals(request.useLlmKeywordExtraction()));
        }
        RagIndexJobSourceRequest sourceRequest = indexRequest == null
                ? new RagIndexJobSourceRequest(
                        metadata,
                        request.keywords() == null ? List.of() : request.keywords(),
                        Boolean.TRUE.equals(request.useLlmKeywordExtraction()))
                : null;
        return new CreateJobCommand(new RagIndexJobCreateRequest(
                request.objectType(),
                request.objectId(),
                documentId,
                request.sourceType(),
                Boolean.TRUE.equals(request.forceReindex()),
                indexRequest), sourceRequest);
    }

    private boolean isAttachmentSource(RagIndexJobCreateRequestDto request) {
        return "attachment".equalsIgnoreCase(request.sourceType());
    }

    private void dispatch(String jobId, Runnable task) {
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    task.run();
                } catch (RuntimeException ex) {
                    log.warn("RAG index job execution failed for jobId={}: {}", jobId, ex.getMessage(), ex);
                }
            }, jobExecutor);
        } catch (RejectedExecutionException ex) {
            jobService.progressListener(jobId).onError(
                    null,
                    studio.one.platform.ai.core.rag.RagIndexJobLogCode.UNKNOWN_ERROR,
                    "RAG index job dispatch rejected",
                    ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RAG index job executor is busy", ex);
        }
    }

    private RagIndexJob requireJob(String jobId) {
        return jobService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RAG index job not found"));
    }

    private RagIndexChunkDto toChunkDto(RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String documentId = text(firstPresent(metadata, VectorRecord.KEY_DOCUMENT_ID, "documentId", "sourceDocumentId"));
        documentId = documentId == null ? result.documentId() : documentId;
        String chunkId = text(firstPresent(metadata, VectorRecord.KEY_CHUNK_ID, "chunkId"));
        chunkId = chunkId == null ? documentId : chunkId;
        return new RagIndexChunkDto(
                chunkId,
                documentId,
                text(firstPresent(metadata, VectorRecord.KEY_PARENT_CHUNK_ID, "parentChunkId")),
                integer(firstPresent(metadata, "chunkOrder", VectorRecord.KEY_CHUNK_INDEX)),
                text(firstPresent(metadata, VectorRecord.KEY_CHUNK_TYPE, "chunkType")),
                result.content(),
                result.score(),
                text(firstPresent(metadata, VectorRecord.KEY_HEADING_PATH, "headingPath", "section")),
                text(firstPresent(metadata, VectorRecord.KEY_SOURCE_REF, "sourceRef", "sourceRefs")),
                integer(firstPresent(metadata, VectorRecord.KEY_PAGE, "page")),
                integer(firstPresent(metadata, VectorRecord.KEY_SLIDE, "slide")),
                metadata,
                instant(firstPresent(metadata, "createdAt")),
                instant(firstPresent(metadata, "indexedAt")));
    }

    private Object firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                    .filter(Objects::nonNull)
                    .map(Objects::toString)
                    .filter(text -> !text.isBlank())
                    .reduce((left, right) -> left + " > " + right)
                    .orElse(null);
        }
        String text = Objects.toString(value, null);
        return text == null || text.isBlank() ? null : text;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Instant.parse(text.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private record CreateJobCommand(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
    }
}
