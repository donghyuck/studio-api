package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import studio.one.platform.ai.core.rag.RagEmbeddingSelectionInfo;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceNameResolver;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.pipeline.RagObjectMetadataContributor;
import studio.one.platform.ai.web.dto.RagIndexChunkDto;
import studio.one.platform.ai.web.dto.RagIndexJobCreateRequestDto;
import studio.one.platform.ai.web.dto.RagIndexJobDto;
import studio.one.platform.ai.web.dto.RagIndexJobLogDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;
import studio.one.platform.web.dto.PageDto;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag")
@Validated
public class RagIndexJobController {

    private static final Logger log = LoggerFactory.getLogger(RagIndexJobController.class);
    private static final int DEFAULT_JOB_PAGE_SIZE = 50;
    private static final int MAX_JOB_PAGE_SIZE = 200;
    private static final int DEFAULT_CHUNK_LIMIT = 200;

    private final RagIndexJobService jobService;
    private final RagPipelineService ragPipelineService;
    private final Executor jobExecutor;
    private final int maxChunkPageLimit;
    private final List<RagIndexJobSourceNameResolver> sourceNameResolvers;
    private final List<RagObjectMetadataContributor> metadataContributors;
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
        this(jobService, ragPipelineService, vectorStorePort, jobExecutor, DEFAULT_CHUNK_LIMIT);
    }

    public RagIndexJobController(
            RagIndexJobService jobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort,
            Executor jobExecutor,
            int maxChunkPageLimit) {
        this(jobService, ragPipelineService, vectorStorePort, jobExecutor, maxChunkPageLimit, List.of());
    }

    public RagIndexJobController(
            RagIndexJobService jobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort,
            Executor jobExecutor,
            int maxChunkPageLimit,
            List<RagIndexJobSourceNameResolver> sourceNameResolvers) {
        this(jobService, ragPipelineService, vectorStorePort, jobExecutor, maxChunkPageLimit,
                sourceNameResolvers, List.of());
    }

    public RagIndexJobController(
            RagIndexJobService jobService,
            RagPipelineService ragPipelineService,
            @Nullable VectorStorePort vectorStorePort,
            Executor jobExecutor,
            int maxChunkPageLimit,
            List<RagIndexJobSourceNameResolver> sourceNameResolvers,
            List<RagObjectMetadataContributor> metadataContributors) {
        this.jobService = Objects.requireNonNull(jobService, "jobService");
        this.ragPipelineService = Objects.requireNonNull(ragPipelineService, "ragPipelineService");
        this.vectorStorePort = vectorStorePort;
        this.jobExecutor = Objects.requireNonNull(jobExecutor, "jobExecutor");
        this.maxChunkPageLimit = maxChunkPageLimit <= 0 ? DEFAULT_CHUNK_LIMIT : maxChunkPageLimit;
        this.sourceNameResolvers = sourceNameResolvers == null ? List.of() : List.copyOf(sourceNameResolvers);
        this.metadataContributors = metadataContributors == null ? List.of() : List.copyOf(metadataContributors);
    }

    @GetMapping("/jobs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<PageDto<RagIndexJobDto>>> listJobs(
            @RequestParam(name = "status", required = false) RagIndexJobStatus status,
            @RequestParam(name = "objectType", required = false) String objectType,
            @RequestParam(name = "objectId", required = false) String objectId,
            @RequestParam(name = "documentId", required = false) String documentId,
            @PageableDefault(size = DEFAULT_JOB_PAGE_SIZE) Pageable pageable,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", required = false) String direction) {
        Pageable boundedPageable = boundedJobPageable(pageable);
        RagIndexJobPage page = jobService.listJobs(
                new RagIndexJobFilter(status, objectType, objectId, documentId),
                new RagIndexJobPageRequest(pageOffset(boundedPageable), boundedPageable.getPageSize()),
                new RagIndexJobSort(
                        RagIndexJobSort.Field.from(sort),
                        RagIndexJobSort.Direction.from(direction)));
        return ResponseEntity.ok(ApiResponse.ok(PageDto.from(new PageImpl<>(
                page.jobs().stream().map(this::toJobDto).toList(),
                boundedPageable,
                page.total()))));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagIndexJobDto>> getJob(@PathVariable("jobId") String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(toJobDto(requireJob(jobId))));
    }

    @PostMapping("/jobs")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','write')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentSource(#request)"
            + " or @endpointAuthz.can('features:attachment','write'))")
    public ResponseEntity<ApiResponse<RagIndexJobDto>> createJob(
            @Valid @RequestBody RagIndexJobCreateRequestDto request) {
        CreateJobCommand command = toCreateRequest(request);
        RagIndexJob job = command.sourceRequest() == null
                ? jobService.createJob(command.request())
                : jobService.createJob(command.request(), command.sourceRequest());
        dispatch(job.jobId(), () -> jobService.startJob(job.jobId()));
        return ResponseEntity.accepted().body(ApiResponse.ok(toJobDto(job)));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','write')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentJob(#jobId)"
            + " or @endpointAuthz.can('features:attachment','write'))")
    public ResponseEntity<ApiResponse<RagIndexJobDto>> retryJob(@PathVariable("jobId") String jobId) {
        RagIndexJob job = requireJob(jobId);
        if (job.status() == RagIndexJobStatus.PENDING || job.status() == RagIndexJobStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RAG index job is still active");
        }
        dispatch(jobId, () -> jobService.retryJob(jobId));
        return ResponseEntity.accepted().body(ApiResponse.ok(toJobDto(requireJob(jobId))));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','write')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentJob(#jobId)"
            + " or @endpointAuthz.can('features:attachment','write'))")
    public ResponseEntity<ApiResponse<RagIndexJobDto>> cancelJob(@PathVariable("jobId") String jobId) {
        requireJob(jobId);
        try {
            RagIndexJob cancelled = jobService.cancelJob(jobId);
            return ResponseEntity.accepted().body(ApiResponse.ok(toJobDto(cancelled)));
        } catch (UnsupportedOperationException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "RAG index job cancel is not supported", ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
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
    public ResponseEntity<ApiResponse<PageDto<RagIndexChunkDto>>> getJobChunks(
            @PathVariable("jobId") String jobId,
            @PageableDefault(size = DEFAULT_CHUNK_LIMIT) Pageable pageable) {
        RagIndexJob job = requireJob(jobId);
        if (job.objectType() == null || job.objectId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job has no object scope");
        }
        return objectChunks(job.objectType(), job.objectId(), pageable);
    }

    @GetMapping("/jobs/{jobId}/chunks/page")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentJob(#jobId)"
            + " or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<PageDto<RagIndexChunkDto>>> getJobChunksPage(
            @PathVariable("jobId") String jobId,
            @PageableDefault(size = DEFAULT_CHUNK_LIMIT) Pageable pageable) {
        RagIndexJob job = requireJob(jobId);
        if (job.objectType() == null || job.objectId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job has no object scope");
        }
        return objectChunksPage(job.objectType(), job.objectId(), pageable);
    }

    @GetMapping("/objects/{objectType}/{objectId}/chunks")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentObject(#objectType)"
            + " or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<PageDto<RagIndexChunkDto>>> objectChunks(
            @PathVariable("objectType") String objectType,
            @PathVariable("objectId") String objectId,
            @PageableDefault(size = DEFAULT_CHUNK_LIMIT) Pageable pageable) {
        return objectChunksPage(objectType, objectId, pageable);
    }

    @GetMapping("/objects/{objectType}/{objectId}/chunks/page")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentObject(#objectType)"
            + " or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<PageDto<RagIndexChunkDto>>> objectChunksPage(
            @PathVariable("objectType") String objectType,
            @PathVariable("objectId") String objectId,
            @PageableDefault(size = DEFAULT_CHUNK_LIMIT) Pageable pageable) {
        Pageable boundedPageable = boundedChunkPageable(pageable);
        List<RagIndexChunkDto> items = ragPipelineService
                .listByObject(
                        objectType,
                        objectId,
                        pageOffset(boundedPageable),
                        boundedPageable.getPageSize())
                .stream()
                .map(this::toChunkDto)
                .toList();
        long total = ragPipelineService.countByObject(objectType, objectId);
        return ResponseEntity.ok(ApiResponse.ok(PageDto.from(new PageImpl<>(items, boundedPageable, total))));
    }

    @DeleteMapping("/objects/{objectType}/{objectId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','write')"
            + " and (!@ragIndexJobEndpointSecurity.isAttachmentObject(#objectType)"
            + " or @endpointAuthz.can('features:attachment','write'))")
    public ResponseEntity<ApiResponse<Void>> deleteObject(
            @PathVariable("objectType") String objectType,
            @PathVariable("objectId") String objectId) {
        String normalizedObjectType = pathSegment(objectType);
        String normalizedObjectId = pathSegment(objectId);
        if (normalizedObjectType == null || normalizedObjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "objectType and objectId are required");
        }
        rejectActiveObjectJob(normalizedObjectType, normalizedObjectId);
        try {
            ragPipelineService.deleteByObject(normalizedObjectType, normalizedObjectId);
            jobService.deleteObjectHistory(normalizedObjectType, normalizedObjectId);
        } catch (UnsupportedOperationException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "RAG object delete is not supported", ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
        return ResponseEntity.ok(ApiResponse.ok());
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
        Map<String, Object> stored = vectorStorePort.getMetadata(objectType, objectId);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("objectType", objectType);
        metadata.put("objectId", objectId);
        metadata.put("indexed", stored != null && !stored.isEmpty());
        if (stored != null) {
            metadata.putAll(stored);
        }
        Map<String, Object> embedding = embeddingMetadata(stored);
        if (!embedding.isEmpty()) {
            metadata.put("embedding", embedding);
        }
        for (RagObjectMetadataContributor contributor : metadataContributors) {
            Map<String, Object> contributed = contributor.contribute(objectType, objectId);
            if (contributed != null && !contributed.isEmpty()) {
                metadata.putAll(contributed);
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(metadata));
    }

    private Map<String, Object> embeddingMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> embedding = new HashMap<>();
        copyMetadata(metadata, embedding, "embeddingProvider", "provider");
        copyMetadata(metadata, embedding, "embeddingModel", "model");
        copyMetadata(metadata, embedding, "embeddingDimension", "dimension");
        return Map.copyOf(embedding);
    }

    private void copyMetadata(Map<String, Object> source, Map<String, Object> target,
            String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            target.put(targetKey, value);
        }
    }

    private void rejectActiveObjectJob(String objectType, String objectId) {
        for (RagIndexJobStatus status : List.of(RagIndexJobStatus.PENDING, RagIndexJobStatus.RUNNING)) {
            RagIndexJobPage page = jobService.listJobs(
                    new RagIndexJobFilter(status, objectType, objectId, null),
                    new RagIndexJobPageRequest(0, 1),
                    RagIndexJobSort.defaults());
            Optional<RagIndexJob> activeJob = page.jobs().stream()
                    .filter(job -> job.status() == status)
                    .findFirst();
            if (activeJob.isPresent()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "RAG index object cannot be deleted while job is active: "
                                + activeJob.get().jobId());
            }
        }
    }

    private RagIndexJobDto toJobDto(RagIndexJob job) {
        return RagIndexJobDto.from(job, embeddingSelection(job).orElse(null));
    }

    private Optional<RagEmbeddingSelectionInfo> embeddingSelection(RagIndexJob job) {
        Optional<RagEmbeddingSelectionInfo> requestSelection = jobService.getEmbeddingSelection(job.jobId());
        if (vectorStorePort == null || job.objectType() == null || job.objectId() == null) {
            return requestSelection;
        }
        try {
            Map<String, Object> metadata = vectorStorePort.getMetadata(job.objectType(), job.objectId());
            RagEmbeddingSelectionInfo requested = requestSelection.orElse(null);
            RagEmbeddingSelectionInfo selection = new RagEmbeddingSelectionInfo(
                    firstNonBlank(text(metadata.get(VectorRecord.KEY_EMBEDDING_PROFILE_ID)),
                            requested == null ? null : requested.embeddingProfileId()),
                    firstNonBlank(text(metadata.get(VectorRecord.KEY_EMBEDDING_PROVIDER)),
                            requested == null ? null : requested.embeddingProvider()),
                    firstNonBlank(text(metadata.get(VectorRecord.KEY_EMBEDDING_MODEL)),
                            requested == null ? null : requested.embeddingModel()),
                    firstNonBlank(text(metadata.get(VectorRecord.KEY_EMBEDDING_DEPLOYMENT_ID)),
                            requested == null ? job.embeddingDeploymentId() : requested.embeddingDeploymentId()),
                    firstNonBlank(text(metadata.get(VectorRecord.KEY_EMBEDDING_CATALOG_ID)),
                            requested == null ? job.catalogId() : requested.catalogId()),
                    firstNonBlank(firstNonBlank(
                                    text(metadata.get(VectorRecord.KEY_EMBEDDING_SPACE_ID_V2)),
                                    text(metadata.get(VectorRecord.KEY_EMBEDDING_SPACE_ID))),
                            requested == null ? job.embeddingSpaceId() : requested.embeddingSpaceId()));
            return selection.empty() ? Optional.empty() : Optional.of(selection);
        } catch (UnsupportedOperationException ex) {
            return requestSelection;
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        return hasText(primary) ? primary.trim() : hasText(fallback) ? fallback.trim() : null;
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
                    Boolean.TRUE.equals(request.useLlmKeywordExtraction()),
                    request.embeddingProfileId(),
                    request.embeddingProvider(),
                    request.embeddingModel(),
                    null,
                    embeddingDeploymentId(request.embeddingDeploymentId(), request.embeddingModelId()));
        }
        RagIndexJobSourceRequest sourceRequest = indexRequest == null
                ? new RagIndexJobSourceRequest(
                        metadata,
                        request.keywords() == null ? List.of() : request.keywords(),
                        Boolean.TRUE.equals(request.useLlmKeywordExtraction()),
                        request.embeddingProfileId(),
                        request.embeddingProvider(),
                        request.embeddingModel(),
                        null,
                        false,
                        embeddingDeploymentId(request.embeddingDeploymentId(), request.embeddingModelId()))
                : null;
        RagIndexJobCreateRequest createRequest = new RagIndexJobCreateRequest(
                request.objectType(),
                request.objectId(),
                documentId,
                request.sourceType(),
                Boolean.TRUE.equals(request.forceReindex()),
                indexRequest);
        String sourceName = sourceName(request, metadata, createRequest, sourceRequest);
        return new CreateJobCommand(new RagIndexJobCreateRequest(
                request.objectType(),
                request.objectId(),
                documentId,
                request.sourceType(),
                Boolean.TRUE.equals(request.forceReindex()),
                indexRequest,
                sourceName), sourceRequest);
    }

    private boolean isAttachmentSource(RagIndexJobCreateRequestDto request) {
        return "attachment".equalsIgnoreCase(request.sourceType());
    }

    private String sourceName(
            RagIndexJobCreateRequestDto request,
            Map<String, Object> metadata,
            RagIndexJobCreateRequest createRequest,
            @Nullable RagIndexJobSourceRequest sourceRequest) {
        String sourceName = text(request.sourceName());
        if (sourceName != null) {
            return sourceName;
        }
        sourceName = text(firstPresent(metadata, "sourceName", "title", "filename", "fileName", "name"));
        if (sourceName != null) {
            return sourceName;
        }
        return resolveSourceName(createRequest, sourceRequest).orElse(createRequest.documentId());
    }

    private Optional<String> resolveSourceName(
            RagIndexJobCreateRequest createRequest,
            @Nullable RagIndexJobSourceRequest sourceRequest) {
        if (sourceRequest == null) {
            return Optional.empty();
        }
        for (RagIndexJobSourceNameResolver resolver : sourceNameResolvers) {
            try {
                if (!resolver.supports(createRequest, sourceRequest)) {
                    continue;
                }
                Optional<String> resolved = resolver.resolveSourceName(createRequest, sourceRequest)
                        .map(this::text)
                        .filter(Objects::nonNull);
                if (resolved.isPresent()) {
                    return resolved;
                }
            } catch (RuntimeException ex) {
                log.debug("RAG index job sourceName resolver failed: {}", ex.getMessage(), ex);
            }
        }
        return Optional.empty();
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

    private Pageable boundedJobPageable(Pageable pageable) {
        Pageable source = pageable == null || pageable.isUnpaged()
                ? PageRequest.of(0, DEFAULT_JOB_PAGE_SIZE)
                : pageable;
        int page = Math.max(0, source.getPageNumber());
        int requestedSize = source.getPageSize() <= 0 ? DEFAULT_JOB_PAGE_SIZE : source.getPageSize();
        int size = Math.min(requestedSize, MAX_JOB_PAGE_SIZE);
        return PageRequest.of(page, size, source.getSort());
    }

    private int pageOffset(Pageable pageable) {
        long offset = pageable.getOffset();
        if (offset > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page offset is too large");
        }
        return (int) offset;
    }

    private Pageable boundedChunkPageable(Pageable pageable) {
        Pageable source = pageable == null || pageable.isUnpaged()
                ? PageRequest.of(0, Math.min(DEFAULT_CHUNK_LIMIT, maxChunkPageLimit))
                : pageable;
        int page = Math.max(0, source.getPageNumber());
        int requestedSize = source.getPageSize() <= 0
                ? Math.min(DEFAULT_CHUNK_LIMIT, maxChunkPageLimit)
                : source.getPageSize();
        int size = Math.min(requestedSize, maxChunkPageLimit);
        return PageRequest.of(page, size, source.getSort());
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

    private String embeddingDeploymentId(String deploymentId, String legacyModelId) {
        String canonical = hasText(deploymentId) ? deploymentId.trim() : null;
        String legacy = hasText(legacyModelId) ? legacyModelId.trim() : null;
        if (canonical != null && legacy != null && !canonical.equalsIgnoreCase(legacy)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "embeddingDeploymentId and legacy embeddingModelId must identify the same deployment");
        }
        return canonical == null ? legacy : canonical;
    }

    private String pathSegment(String value) {
        return hasText(value) ? value.trim() : null;
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
