/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file AttachmentEmbeddingPipelineController.java
 *      @date 2025
 *
 */

package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.dto.EmbeddingResponseDto;
import studio.one.application.web.dto.EmbeddingVectorDto;
import studio.one.application.web.dto.SearchRequest;
import studio.one.application.web.dto.SearchResponse;
import studio.one.application.web.dto.SearchResult;
import studio.one.application.web.service.AttachmentRagIndexCommand;
import studio.one.application.web.service.AttachmentRagIndexDiagnostics;
import studio.one.application.web.service.AttachmentRagIndexResult;
import studio.one.application.web.service.AttachmentRagIndexService;
import studio.one.application.web.service.AttachmentRagIndexUnavailableException;
import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagChunkingOptions;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.service.I18n;
import studio.one.platform.textract.service.FileContentExtractionService;
import studio.one.platform.web.dto.ApiResponse;

/**
 * 첨부파일 내용을 텍스트로 추출한 뒤 임베딩을 생성하고, 설정된 벡터 스토어에 저장하는 REST 컨트롤러.
 *
 * @author donghyuck, son
 * @since 2025-11-28
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-28  donghyuck, son: 최초 생성.
 *          </pre>
 */

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.mgmt-base-path:/api/mgmt/attachments}")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AttachmentEmbeddingPipelineController {
    private static final String HEADER_RAG_INDEX_PATH = "X-RAG-Index-Path";
    private static final String HEADER_RAG_INDEX_STRUCTURED = "X-RAG-Index-Structured";
    private static final String HEADER_RAG_INDEX_FALLBACK_REASON = "X-RAG-Index-Fallback-Reason";
    private static final String HEADER_RAG_INDEX_PARSED_BLOCK_COUNT = "X-RAG-Index-Parsed-Block-Count";
    private static final String HEADER_RAG_INDEX_CHUNK_COUNT = "X-RAG-Index-Chunk-Count";
    private static final String HEADER_RAG_INDEX_VECTOR_COUNT = "X-RAG-Index-Vector-Count";
    private static final String HEADER_RAG_JOB_ID = "X-RAG-Job-Id";

    private final AttachmentService attachmentService;
    private final ObjectProvider<FileContentExtractionService> textExtractionProvider;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;
    private final ObjectProvider<VectorStorePort> vectorStoreProvider;
    private final ObjectProvider<RagPipelineService> ragPipelineProvider;
    private final ObjectProvider<RagIndexJobService> ragIndexJobServiceProvider;
    private final AttachmentRagIndexService attachmentRagIndexService;
    private final ObjectProvider<I18n> i18nProvider;

    @Value("${studio.ai.endpoints.rag.diagnostics.allow-client-debug:false}")
    private boolean allowClientDebug;

    /**
     * 첨부파일 텍스트를 추출해 임베딩을 생성하고, 구성된 경우 벡터 스토어에 업서트한다.
     * <p>
     * 예시:
     * 
     * <pre>
     * GET /api/mgmt/attachments/{id}/embedding?storeVector=true
     * Authorization: Bearer &lt;token&gt;  (features:attachment write)
     *
     * 200 OK
     * {
     *   "data": {
     *     "vectors": [{"referenceId":"0","values":[0.1,0.2,...]}]
     *   }
     * }
     * </pre>
     * 
     * storeVector 파라미터(기본 true)를 false로 주면 벡터 스토어 저장 없이 임베딩만 반환한다.
     * 텍스트 추출기나 임베딩 어댑터가 없으면 501을 반환하며, storeVector=true인데 벡터 어댑터가 없을 때도 501을 반환한다.
     */
    @GetMapping("/{attachmentId:[\\p{Digit}]+}/embedding")
    @PreAuthorize("@endpointAuthz.can('features:attachment','write')")
    public ResponseEntity<ApiResponse<EmbeddingResponseDto>> embed(@PathVariable("attachmentId") long attachmentId,
            @RequestParam(name = "storeVector", defaultValue = "true") boolean storeVector)
            throws NotFoundException, IOException {

        if (attachmentId <= 0) {
            ApiResponse<EmbeddingResponseDto> body = ApiResponse.<EmbeddingResponseDto>builder()
                    .message("attachmentId must be positive")
                    .build();
            return ResponseEntity.badRequest().body(body);
        }

        FileContentExtractionService extractor = textExtractionProvider.getIfAvailable();
        if (extractor == null) {
            ApiResponse<EmbeddingResponseDto> body = ApiResponse.<EmbeddingResponseDto>builder()
                    .message("Text extraction is not configured")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }

        EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
        if (embeddingPort == null) {
            ApiResponse<EmbeddingResponseDto> body = ApiResponse.<EmbeddingResponseDto>builder()
                    .message("Embedding provider is not configured")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }

        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        try (InputStream in = attachmentService.getInputStream(attachment)) {
            String text = extractor.extractText(attachment.getContentType(), attachment.getName(), in);
            EmbeddingResponse response = embeddingPort.embed(new EmbeddingRequest(List.of(text)));
            EmbeddingResponseDto payload = new EmbeddingResponseDto(toEmbeddingVectors(response));
            if (storeVector) {
                VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
                if (vectorStore == null) {
                    ApiResponse<EmbeddingResponseDto> body = ApiResponse.<EmbeddingResponseDto>builder()
                            .message("Vector store is not configured")
                            .build();
                    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
                }
                upsertVectorDocument(vectorStore, attachment, text, response.vectors().get(0));
            }
            return ResponseEntity.ok(ApiResponse.ok(payload));
        }
    }

    /**
     * 특정 attachmentId에 대한 벡터 데이터 존재 여부를 반환한다.
     * <pre>
     * GET /api/mgmt/attachments/{id}/embedding/exists
     * Authorization: Bearer &lt;token&gt; (features:attachment write)
     *
     * 200 OK
     * {
     *   "data": true
     * }
     * </pre>
     * 벡터 스토어가 없으면 501을 반환한다.
     */
    @GetMapping("/{attachmentId:[\\p{Digit}]+}/embedding/exists")
    @PreAuthorize("@endpointAuthz.can('features:attachment','write')")
    public ResponseEntity<ApiResponse<Boolean>> exists(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException {
        if (attachmentId <= 0) {
            ApiResponse<Boolean> body = ApiResponse.<Boolean>builder()
                    .message("attachmentId must be positive")
                    .build();
            return ResponseEntity.badRequest().body(body);
        }
        VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            ApiResponse<Boolean> body = ApiResponse.<Boolean>builder()
                    .message("Vector store is not configured")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }
        // attachment 존재 여부 확인
        attachmentService.getAttachmentById(attachmentId);
        boolean exists = vectorStore.exists("attachment", String.valueOf(attachmentId));
        return ResponseEntity.ok(ApiResponse.ok(exists));
    }

    /**
     * 첨부파일 벡터 메타데이터 조회.
     */
    @GetMapping("/{attachmentId:[\\p{Digit}]+}/rag/metadata")
    @PreAuthorize("@endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> metadata(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException {
        if (attachmentId <= 0) {
            ApiResponse<Map<String, Object>> body = ApiResponse.<Map<String, Object>>builder()
                    .message("attachmentId must be positive")
                    .build();
            return ResponseEntity.badRequest().body(body);
        }
        VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            ApiResponse<Map<String, Object>> body = ApiResponse.<Map<String, Object>>builder()
                    .message("Vector store is not configured")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }
        // 첨부 존재 확인
        attachmentService.getAttachmentById(attachmentId);
        Map<String, Object> metadata = vectorStore.getMetadata("attachment", String.valueOf(attachmentId));
        return ResponseEntity.ok(ApiResponse.ok(metadata));
    }

    


    /**
     * 첨부파일 내용을 RAG 인덱스에 등록한다.
     */
    @PostMapping("/{attachmentId:[\\p{Digit}]+}/rag/index")
    @PreAuthorize("@endpointAuthz.can('features:attachment','write')")
    public ResponseEntity<Void> ragIndex(@PathVariable("attachmentId") long attachmentId,
            @RequestBody(required = false) AttachmentRagIndexRequestDto request)
            throws NotFoundException, IOException {
        if (attachmentId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (!attachmentRagIndexService.hasTextExtractor()) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        AttachmentRagIndexCommand command = attachmentRagIndexService.command(
                attachmentId,
                request == null ? null : request.documentId(),
                request == null ? null : request.objectType(),
                request == null ? null : request.objectId(),
                request == null ? null : request.metadata(),
                request == null ? null : request.keywords(),
                request == null ? null : request.useLlmKeywordExtraction(),
                request == null ? null : request.embeddingProfileId(),
                request == null ? null : request.embeddingProvider(),
                request == null ? null : request.embeddingModel(),
                chunkingOptions(request));
        RagIndexJobService jobService = ragIndexJobServiceProvider.getIfAvailable();
        RagIndexJob job = createJob(jobService, command);
        RagIndexProgressListener progress = job == null
                ? RagIndexProgressListener.noop()
                : jobService.progressListener(job.jobId());
        progress.onStarted();
        try {
            AttachmentRagIndexResult result = attachmentRagIndexService.index(attachmentId, command, progress);
            progress.onCompleted();
            return accepted(request, result.diagnostics(), job);
        } catch (AttachmentRagIndexUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException | RuntimeException ex) {
            throw ex;
        }
    }

    private ResponseEntity<Void> accepted(
            AttachmentRagIndexRequestDto request,
            AttachmentRagIndexDiagnostics diagnostics,
            RagIndexJob job) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.accepted();
        if (job != null) {
            builder.header(HEADER_RAG_JOB_ID, job.jobId());
        }
        if (allowClientDebug && request != null && Boolean.TRUE.equals(request.debug()) && diagnostics != null) {
            builder.header(HEADER_RAG_INDEX_PATH, diagnostics.path());
            builder.header(HEADER_RAG_INDEX_STRUCTURED, Boolean.toString(diagnostics.structured()));
            putHeader(builder, HEADER_RAG_INDEX_FALLBACK_REASON, diagnostics.fallbackReason());
            putCountHeader(builder, HEADER_RAG_INDEX_PARSED_BLOCK_COUNT, diagnostics.parsedBlockCount());
            putCountHeader(builder, HEADER_RAG_INDEX_CHUNK_COUNT, diagnostics.chunkCount());
            putCountHeader(builder, HEADER_RAG_INDEX_VECTOR_COUNT, diagnostics.vectorCount());
        }
        return builder.build();
    }

    private RagIndexJob createJob(
            RagIndexJobService jobService,
            AttachmentRagIndexCommand command) {
        if (jobService == null) {
            return null;
        }
        return jobService.createJob(new RagIndexJobCreateRequest(
                command.objectType(),
                command.objectId(),
                command.documentId(),
                "attachment",
                false,
                null),
                new RagIndexJobSourceRequest(
                        command.metadata(),
                        command.keywords(),
                        command.useLlmKeywordExtraction(),
                        command.embeddingProfileId(),
                        command.embeddingProvider(),
                        command.embeddingModel(),
                        command.chunkingOptions()));
    }

    private RagChunkingOptions chunkingOptions(AttachmentRagIndexRequestDto request) {
        if (request == null) {
            return RagChunkingOptions.empty();
        }
        try {
            return new RagChunkingOptions(
                    request.chunkingStrategy(),
                    request.chunkMaxSize(),
                    request.chunkOverlap(),
                    request.chunkUnit());
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private void putHeader(ResponseEntity.BodyBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.header(name, value);
        }
    }

    private void putCountHeader(ResponseEntity.BodyBuilder builder, String name, int value) {
        if (value >= 0) {
            builder.header(name, Integer.toString(value));
        }
    }

    /**
     * 첨부파일 RAG 색인에 대해 검색을 수행한다.
     */
    @PostMapping("/rag/search")
    @PreAuthorize("@endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<SearchResponse> ragSearch(@Validated @RequestBody SearchRequest request) {
        RagPipelineService ragPipeline = ragPipelineProvider.getIfAvailable();
        if (ragPipeline == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        MetadataFilter filter = MetadataFilter.objectScope(request.objectType(), request.objectId());
        List<RagSearchResult> results = ragPipeline.search(new RagSearchRequest(
                request.query(),
                request.topK(),
                filter,
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel()));
        List<SearchResult> payload = results.stream()
                .map(result -> new SearchResult(
                        result.documentId(),
                        result.content(),
                        result.metadata(),
                        result.score()))
                .toList();
        return ResponseEntity.ok(new SearchResponse(payload));
    }

    private List<EmbeddingVectorDto> toEmbeddingVectors(EmbeddingResponse response) {
        return response.vectors().stream()
                .map(vector -> new EmbeddingVectorDto(vector.referenceId(), List.copyOf(vector.values())))
                .toList();
    }

    private void upsertVectorDocument(VectorStorePort vectorStore, Attachment attachment, String text, EmbeddingVector vector) {
        try {
            Map<String, Object> metadata = Map.of(
                    "objectType", "attachment",
                    "objectId", attachment.getAttachmentId(),
                    "attachmentId", attachment.getAttachmentId(),
                    "name", attachment.getName(),
                    "contentType", attachment.getContentType(),
                    "size", attachment.getSize(),
                    "chunkOrder", 0);
            VectorDocument document = new VectorDocument(
                    String.valueOf(attachment.getAttachmentId()),
                    text,
                    metadata,
                    List.copyOf(vector.values()));
            vectorStore.upsert(List.of(document));
        } catch (Exception ex) {
            log.warn("Failed to persist embedding for attachment {}", attachment.getAttachmentId(), ex);
        }
    }
}
