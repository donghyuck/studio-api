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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
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
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.EmbeddingResponseDto;
import studio.one.platform.ai.web.dto.EmbeddingVectorDto;
import studio.one.platform.ai.web.dto.SearchRequest;
import studio.one.platform.ai.web.dto.SearchResponse;
import studio.one.platform.ai.web.dto.SearchResult;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.service.I18n;
import studio.one.platform.text.service.FileContentExtractionService;
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
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.base-path:/api/mgmt/attachments}")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AttachmentEmbeddingPipelineController {

    private final AttachmentService attachmentService;
    private final ObjectProvider<FileContentExtractionService> textExtractionProvider;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;
    private final ObjectProvider<VectorStorePort> vectorStoreProvider;
    private final ObjectProvider<RagPipelineService> ragPipelineProvider;
    private final ObjectProvider<I18n> i18nProvider;

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
        RagPipelineService ragPipeline = ragPipelineProvider.getIfAvailable();
        FileContentExtractionService extractor = textExtractionProvider.getIfAvailable();
        if (ragPipeline == null || extractor == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        try (InputStream in = attachmentService.getInputStream(attachment)) {
            String text = extractor.extractText(attachment.getContentType(), attachment.getName(), in);
            String documentId = resolveDocumentId(request, attachmentId);
            String objectType = resolveObjectType(request);
            String objectId = resolveObjectId(request, attachmentId);
            Map<String, Object> metadata = buildMetadata(request, attachment, objectType, objectId);
            List<String> keywords = request != null && request.keywords() != null ? request.keywords() : List.of();
            boolean useLlmKeywords = request != null && Boolean.TRUE.equals(request.useLlmKeywordExtraction()); 
            ragPipeline.index(new RagIndexRequest(documentId, text, metadata, keywords, useLlmKeywords));
        }
        return ResponseEntity.accepted().build();
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
        List<RagSearchResult> results = ragPipeline.search(new RagSearchRequest(request.query(), request.topK()));
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

    private String resolveDocumentId(AttachmentRagIndexRequestDto request, long attachmentId) {
        if (request != null && request.documentId() != null && !request.documentId().isBlank()) {
            return request.documentId().trim();
        }
        return String.valueOf(attachmentId);
    }

    private String resolveObjectType(AttachmentRagIndexRequestDto request) {
        if (request != null && request.objectType() != null && !request.objectType().isBlank()) {
            return request.objectType().trim();
        }
        return "attachment";
    }

    private String resolveObjectId(AttachmentRagIndexRequestDto request, long attachmentId) {
        if (request != null && request.objectId() != null && !request.objectId().isBlank()) {
            return request.objectId().trim();
        }
        return String.valueOf(attachmentId);
    }

    private Map<String, Object> buildMetadata(AttachmentRagIndexRequestDto request,
            Attachment attachment,
            String objectType,
            String objectId) {
        Map<String, Object> metadata = request != null && request.metadata() != null
                ? new HashMap<>(request.metadata())
                : new HashMap<>();
        metadata.putIfAbsent("objectType", objectType);
        metadata.putIfAbsent("objectId", objectId);
        metadata.putIfAbsent("attachmentId", attachment.getAttachmentId());
        metadata.putIfAbsent("name", attachment.getName());
        metadata.putIfAbsent("contentType", attachment.getContentType());
        metadata.putIfAbsent("size", attachment.getSize());
        metadata.putIfAbsent("chunkOrder", 0);
        return metadata;
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
