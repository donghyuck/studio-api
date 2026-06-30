package studio.one.platform.markdown.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.markdown.application.MarkdownDocumentService;
import studio.one.platform.markdown.application.MarkdownDocumentNotFoundException;
import studio.one.platform.markdown.application.MarkdownExtractionRequest;
import studio.one.platform.markdown.application.MarkdownExtractionResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeApplyOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeApplyResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeBatchApplyResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergePreview;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergePreviewOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeUndoOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeUndoResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockSummary;
import studio.one.platform.markdown.application.MarkdownPipelineEstimate;
import studio.one.platform.markdown.application.MarkdownPipelineEstimateUnavailableException;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.MarkdownPipelineProgress;
import studio.one.platform.markdown.application.MarkdownResumeOptions;
import studio.one.platform.markdown.application.MarkdownResumeResult;
import studio.one.platform.markdown.application.MarkdownSourceTooLargeException;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownPipelineExecution;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.web.dto.ApiResponse;
import studio.one.platform.web.dto.ProblemDetails;

@RestController
@RequiredArgsConstructor
@RequestMapping("${studio.markdown.web.base-path:/api/markdown-documents}")
public class MarkdownDocumentController {
    private final MarkdownDocumentService service;

    @PostMapping("/from-attachment")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage')")
    public ResponseEntity<ApiResponse<MarkdownExtractionResult>> create(
            @Valid @RequestBody MarkdownDocumentRequest request, Authentication authentication) {
        String actor = authentication == null ? null : authentication.getName();
        var command = new MarkdownExtractionRequest(request.attachmentId(), options(request), request.force(), actor);
        return ResponseEntity.accepted().body(ApiResponse.ok(service.create(command)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownDocument> get(@PathVariable String id) {
        return ApiResponse.ok(service.getDocument(id));
    }

    @GetMapping("/by-attachment/{attachmentId}")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownDocument> getByAttachment(@PathVariable long attachmentId) {
        return ApiResponse.ok(service.getDocumentBySourceAttachmentId(attachmentId));
    }

    @GetMapping("/{id}/revisions")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<List<MarkdownRevision>> revisions(@PathVariable String id) {
        return ApiResponse.ok(service.getRevisions(id));
    }

    @GetMapping("/{id}/revisions/{revisionId}/ideablocks/summary")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownIdeaBlockSummary> ideaBlockSummary(
            @PathVariable String id, @PathVariable String revisionId) {
        return ApiResponse.ok(service.getIdeaBlockSummary(id, revisionId));
    }

    @PostMapping("/{id}/revisions/{revisionId}/ideablocks/merge-preview")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownIdeaBlockMergePreview> ideaBlockMergePreview(
            @PathVariable String id,
            @PathVariable String revisionId,
            @RequestBody(required = false) MarkdownIdeaBlockMergePreviewRequest request) {
        return ApiResponse.ok(service.getIdeaBlockMergePreview(id, revisionId, mergePreviewOptions(request)));
    }

    @PostMapping("/{id}/revisions/{revisionId}/ideablocks/merge-apply")
    @PreAuthorize("@endpointAuthz.can('features:markdown','write')")
    public ApiResponse<MarkdownIdeaBlockMergeApplyResult> ideaBlockMergeApply(
            @PathVariable String id,
            @PathVariable String revisionId,
            @RequestBody MarkdownIdeaBlockMergeApplyRequest request) {
        return ApiResponse.ok(service.applyIdeaBlockMerge(
                id, revisionId, mergeApplyOptions(request), mergeApplyDownstreamOptions(request)));
    }

    @PostMapping("/{id}/revisions/{revisionId}/ideablocks/merge-apply-batch")
    @PreAuthorize("@endpointAuthz.can('features:markdown','write')")
    public ApiResponse<MarkdownIdeaBlockMergeBatchApplyResult> ideaBlockMergeApplyBatch(
            @PathVariable String id,
            @PathVariable String revisionId,
            @RequestBody MarkdownIdeaBlockMergeBatchApplyRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Merge apply batch items are required");
        }
        return ApiResponse.ok(service.applyIdeaBlockMergeBatch(
                id,
                revisionId,
                request.items().stream().map(this::mergeApplyOptions).toList(),
                mergeBatchApplyDownstreamOptions(request)));
    }

    @PostMapping("/{id}/revisions/{revisionId}/ideablocks/merge-auto-apply")
    @PreAuthorize("@endpointAuthz.can('features:markdown','write')")
    public ApiResponse<MarkdownIdeaBlockMergeBatchApplyResult> ideaBlockMergeAutoApply(
            @PathVariable String id,
            @PathVariable String revisionId,
            @RequestBody(required = false) MarkdownIdeaBlockMergeAutoApplyRequest request) {
        return ApiResponse.ok(service.autoApplyIdeaBlockMerge(
                id,
                revisionId,
                mergeAutoApplyPreviewOptions(request),
                mergeAutoApplyDownstreamOptions(request)));
    }

    @PostMapping("/{id}/revisions/{revisionId}/ideablocks/merge-undo")
    @PreAuthorize("@endpointAuthz.can('features:markdown','write')")
    public ApiResponse<MarkdownIdeaBlockMergeUndoResult> ideaBlockMergeUndo(
            @PathVariable String id,
            @PathVariable String revisionId,
            @RequestBody MarkdownIdeaBlockMergeUndoRequest request) {
        return ApiResponse.ok(service.undoIdeaBlockMerge(
                id, revisionId, mergeUndoOptions(request), mergeUndoDownstreamOptions(request)));
    }

    @GetMapping("/{id}/pipeline")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownPipelineExecution> pipeline(@PathVariable String id) {
        return ApiResponse.ok(service.getPipelineExecution(id));
    }

    @GetMapping("/{id}/pipeline/progress")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownPipelineProgress> pipelineProgress(@PathVariable String id) {
        return ApiResponse.ok(service.getPipelineProgress(id));
    }

    @PostMapping("/{id}/pipeline/estimate")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownPipelineEstimate> pipelineEstimate(
            @PathVariable String id, @RequestBody(required = false) MarkdownResumeRequest request) {
        return ApiResponse.ok(service.estimatePipeline(id, resumeOptions(request)));
    }

    @PostMapping("/by-attachment/{attachmentId}/pipeline/estimate")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownPipelineEstimate> pipelineEstimateByAttachment(
            @PathVariable long attachmentId, @RequestBody(required = false) MarkdownResumeRequest request) {
        return ApiResponse.ok(service.estimatePipelineByAttachment(attachmentId, resumeOptions(request)));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage')")
    public ResponseEntity<ApiResponse<MarkdownResumeResult>> resume(
            @PathVariable String id, @RequestBody(required = false) MarkdownResumeRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.ok(
                service.resumeWithOptions(id, resumeOptions(request))));
    }

    @PostMapping("/{id}/rag/reindex")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage')")
    public ResponseEntity<ApiResponse<MarkdownResumeResult>> reindexRag(
            @PathVariable String id, @Valid @RequestBody MarkdownRagReindexRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.ok(service.reindexRag(
                id,
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDimension(),
                request.useLlmKeywordExtraction(),
                request.runSkillExtraction(),
                request.skillExtractionMode(),
                request.generateSkillEmbeddings(),
                request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(),
                request.skillEmbeddingDimension())));
    }

    @GetMapping("/{id}/locators")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<List<MarkdownLocator>> locators(@PathVariable String id) {
        return ApiResponse.ok(service.getLocators(id));
    }

    @GetMapping("/{id}/resources")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<List<MarkdownResource>> resources(@PathVariable String id) {
        return ApiResponse.ok(service.getResources(id));
    }

    @PostMapping("/{id}/reextract")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage')")
    public ResponseEntity<ApiResponse<MarkdownExtractionResult>> reextract(
            @PathVariable String id, @RequestBody MarkdownReextractRequest request, Authentication authentication) {
        String actor = authentication == null ? null : authentication.getName();
        return ResponseEntity.accepted().body(ApiResponse.ok(service.reextract(id, options(request), actor)));
    }

    @DeleteMapping("/{id}/extraction")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage')")
    public ApiResponse<MarkdownRevision> cancel(@PathVariable String id) {
        return ApiResponse.ok(service.cancel(id));
    }

    @ExceptionHandler(MarkdownDocumentNotFoundException.class)
    public ResponseEntity<ProblemDetails> notFound(
            MarkdownDocumentNotFoundException exception, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type("urn:error:markdown-document-not-found")
                .title(HttpStatus.NOT_FOUND.getReasonPhrase())
                .status(HttpStatus.NOT_FOUND.value())
                .detail(exception.getMessage())
                .instance(request.getRequestURI())
                .code("markdown.document.not-found")
                .timestamp(OffsetDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MarkdownSourceTooLargeException.class)
    public ResponseEntity<ProblemDetails> sourceTooLarge(
            MarkdownSourceTooLargeException exception, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type("urn:error:markdown-source-too-large")
                .title(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase())
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .detail("Attachment exceeds markdown source size limit: actualBytes=%d, maxBytes=%d"
                        .formatted(exception.actualBytes(), exception.maxBytes()))
                .instance(request.getRequestURI())
                .code("markdown.source.too-large")
                .timestamp(OffsetDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MarkdownPipelineEstimateUnavailableException.class)
    public ResponseEntity<ProblemDetails> estimateUnavailable(
            MarkdownPipelineEstimateUnavailableException exception, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.builder()
                .type("urn:error:markdown-pipeline-estimate-unavailable")
                .title(HttpStatus.CONFLICT.getReasonPhrase())
                .status(HttpStatus.CONFLICT.value())
                .detail(exception.getMessage())
                .instance(request.getRequestURI())
                .code("markdown.pipeline.estimate-unavailable")
                .timestamp(OffsetDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private MarkdownPipelineOptions options(MarkdownDocumentRequest request) {
        return new MarkdownPipelineOptions(
                request.runChunking(), request.runRagIndex(), request.runSkillExtraction(),
                request.chunkingStrategy(), request.chunkMaxSize(), request.chunkOverlap(), request.chunkUnit(),
                request.blockifyLlmProvider(), request.blockifyLlmModel(), request.blockifyPiiMaskingEnabled(),
                request.embeddingProfileId(), request.embeddingProvider(), request.embeddingModel(),
                request.embeddingDimension(), request.useLlmKeywordExtraction(),
                request.skillExtractionMode(), request.generateSkillEmbeddings(), request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(), request.skillEmbeddingDimension());
    }

    private MarkdownPipelineOptions options(MarkdownReextractRequest request) {
        return new MarkdownPipelineOptions(
                request.runChunking(), request.runRagIndex(), request.runSkillExtraction(),
                request.chunkingStrategy(), request.chunkMaxSize(), request.chunkOverlap(), request.chunkUnit(),
                request.blockifyLlmProvider(), request.blockifyLlmModel(), request.blockifyPiiMaskingEnabled(),
                request.embeddingProfileId(), request.embeddingProvider(), request.embeddingModel(),
                request.embeddingDimension(), request.useLlmKeywordExtraction(),
                request.skillExtractionMode(), request.generateSkillEmbeddings(), request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(), request.skillEmbeddingDimension());
    }

    private MarkdownResumeOptions resumeOptions(MarkdownResumeRequest request) {
        if (request == null) {
            return null;
        }
        return new MarkdownResumeOptions(
                request.fromStage(),
                request.runChunking(),
                request.runRagIndex(),
                request.runSkillExtraction(),
                request.chunkingStrategy(),
                request.chunkMaxSize(),
                request.chunkOverlap(),
                request.chunkUnit(),
                request.blockifyLlmProvider(),
                request.blockifyLlmModel(),
                request.blockifyPiiMaskingEnabled(),
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDimension(),
                request.useLlmKeywordExtraction(),
                request.skillExtractionMode(),
                request.generateSkillEmbeddings(),
                request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(),
                request.skillEmbeddingDimension());
    }

    private MarkdownIdeaBlockMergePreviewOptions mergePreviewOptions(MarkdownIdeaBlockMergePreviewRequest request) {
        if (request == null) {
            return MarkdownIdeaBlockMergePreviewOptions.defaults();
        }
        return new MarkdownIdeaBlockMergePreviewOptions(
                request.clusterId(),
                request.preferEmbeddingClusters() == null || request.preferEmbeddingClusters(),
                request.llmProvider(),
                request.llmModel(),
                request.maxClusters() == null ? 5 : request.maxClusters());
    }

    private MarkdownIdeaBlockMergeApplyOptions mergeApplyOptions(MarkdownIdeaBlockMergeApplyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Merge apply request is required");
        }
        MarkdownIdeaBlockMergePreviewOptions previewOptions = new MarkdownIdeaBlockMergePreviewOptions(
                request.clusterId(),
                request.preferEmbeddingClusters() == null || request.preferEmbeddingClusters(),
                request.llmProvider(),
                request.llmModel(),
                request.maxClusters() == null ? 5 : request.maxClusters());
        return new MarkdownIdeaBlockMergeApplyOptions(previewOptions, request.planFingerprint());
    }

    private MarkdownResumeOptions mergeApplyDownstreamOptions(MarkdownIdeaBlockMergeApplyRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.runRagIndex())) {
            return null;
        }
        return new MarkdownResumeOptions(
                null,
                false,
                true,
                request.runSkillExtraction(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDimension(),
                request.useLlmKeywordExtraction(),
                request.skillExtractionMode(),
                request.generateSkillEmbeddings(),
                request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(),
                request.skillEmbeddingDimension());
    }

    private MarkdownResumeOptions mergeBatchApplyDownstreamOptions(MarkdownIdeaBlockMergeBatchApplyRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.runRagIndex())) {
            return null;
        }
        return new MarkdownResumeOptions(
                null,
                false,
                true,
                request.runSkillExtraction(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDimension(),
                request.useLlmKeywordExtraction(),
                request.skillExtractionMode(),
                request.generateSkillEmbeddings(),
                request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(),
                request.skillEmbeddingDimension());
    }

    private MarkdownIdeaBlockMergePreviewOptions mergeAutoApplyPreviewOptions(
            MarkdownIdeaBlockMergeAutoApplyRequest request) {
        if (request == null) {
            return MarkdownIdeaBlockMergePreviewOptions.defaults();
        }
        return new MarkdownIdeaBlockMergePreviewOptions(
                request.clusterId(),
                request.preferEmbeddingClusters() == null || request.preferEmbeddingClusters(),
                request.llmProvider(),
                request.llmModel(),
                request.maxClusters() == null ? 5 : request.maxClusters());
    }

    private MarkdownResumeOptions mergeAutoApplyDownstreamOptions(MarkdownIdeaBlockMergeAutoApplyRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.runRagIndex())) {
            return null;
        }
        return new MarkdownResumeOptions(
                null,
                false,
                true,
                request.runSkillExtraction(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDimension(),
                request.useLlmKeywordExtraction(),
                request.skillExtractionMode(),
                request.generateSkillEmbeddings(),
                request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(),
                request.skillEmbeddingDimension());
    }

    private MarkdownIdeaBlockMergeUndoOptions mergeUndoOptions(MarkdownIdeaBlockMergeUndoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Merge undo request is required");
        }
        return new MarkdownIdeaBlockMergeUndoOptions(request.mergedChunkId(), request.planFingerprint());
    }

    private MarkdownResumeOptions mergeUndoDownstreamOptions(MarkdownIdeaBlockMergeUndoRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.runRagIndex())) {
            return null;
        }
        return new MarkdownResumeOptions(
                null,
                false,
                true,
                request.runSkillExtraction(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDimension(),
                request.useLlmKeywordExtraction(),
                request.skillExtractionMode(),
                request.generateSkillEmbeddings(),
                request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(),
                request.skillEmbeddingDimension());
    }
}
