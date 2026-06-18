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
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.MarkdownResumeOptions;
import studio.one.platform.markdown.application.MarkdownResumeResult;
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

    @GetMapping("/{id}/pipeline")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<MarkdownPipelineExecution> pipeline(@PathVariable String id) {
        return ApiResponse.ok(service.getPipelineExecution(id));
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

    private MarkdownPipelineOptions options(MarkdownDocumentRequest request) {
        return new MarkdownPipelineOptions(
                request.runChunking(), request.runRagIndex(), request.runSkillExtraction(),
                request.chunkingStrategy(), request.chunkMaxSize(), request.chunkOverlap(), request.chunkUnit(),
                request.embeddingProfileId(), request.embeddingProvider(), request.embeddingModel(),
                request.embeddingDimension(), request.useLlmKeywordExtraction(),
                request.skillExtractionMode(), request.generateSkillEmbeddings(), request.skillEmbeddingProvider(),
                request.skillEmbeddingModel(), request.skillEmbeddingDimension());
    }

    private MarkdownPipelineOptions options(MarkdownReextractRequest request) {
        return new MarkdownPipelineOptions(
                request.runChunking(), request.runRagIndex(), request.runSkillExtraction(),
                request.chunkingStrategy(), request.chunkMaxSize(), request.chunkOverlap(), request.chunkUnit(),
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
