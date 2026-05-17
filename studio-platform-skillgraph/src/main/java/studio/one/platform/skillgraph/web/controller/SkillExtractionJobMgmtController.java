package studio.one.platform.skillgraph.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.web.dto.request.SkillExtractionRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillRagChunkExtractionRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillRagDocumentExtractionRequest;
import studio.one.platform.skillgraph.web.dto.response.SkillExtractionResponse;
import studio.one.platform.skillgraph.web.dto.response.SkillRagBatchExtractionResponse;
import studio.one.platform.skillgraph.web.dto.response.SkillRagChunkExtractionItemDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.extraction-base-path:/api/mgmt/skillgraph/extraction-jobs}")
@Validated
public class SkillExtractionJobMgmtController {

    private static final String RAG_CHUNK_SOURCE_TYPE = "RAG_CHUNK";
    private static final int DEFAULT_RAG_CHUNK_LIMIT = 1000;
    private static final int MAX_RAG_CHUNK_LIMIT = 5000;

    private final SkillExtractionService extractionService;
    private final ObjectProvider<SkillGraphRagChunkResolver> ragChunkResolverProvider;

    public SkillExtractionJobMgmtController(
            SkillExtractionService extractionService,
            ObjectProvider<SkillGraphRagChunkResolver> ragChunkResolverProvider) {
        this.extractionService = Objects.requireNonNull(extractionService, "extractionService");
        this.ragChunkResolverProvider = Objects.requireNonNull(ragChunkResolverProvider,
                "ragChunkResolverProvider");
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage') "
            + "and (@endpointAuthz.can('objects:' + #request.sourceType().trim() + ':' + #request.sourceId().trim(),'read') "
            + "or @endpointAuthz.can('objects:' + #request.sourceType().trim(),'read'))")
    public ResponseEntity<ApiResponse<SkillExtractionResponse>> extract(@Valid @RequestBody SkillExtractionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(SkillExtractionResponse.from(extractionService.extract(
                new SkillExtractionCommand(request.sourceType(), request.sourceId(), request.chunkId(), request.text())))));
    }

    @PostMapping("/rag-documents")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage') "
            + "and @endpointAuthz.can('services:ai_rag','read') "
            + "and (@endpointAuthz.can('objects:' + #request.objectType().trim() + ':' + #request.objectId().trim(),'read') "
            + "or @endpointAuthz.can('objects:' + #request.objectType().trim(),'read'))")
    public ResponseEntity<ApiResponse<SkillRagBatchExtractionResponse>> extractRagDocument(
            @Valid @RequestBody SkillRagDocumentExtractionRequest request) {
        String mode = normalize(request.mode());
        if (mode != null && !"ALL_CHUNKS".equals(mode.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ALL_CHUNKS mode is supported");
        }
        String documentId = normalize(request.documentId());
        List<ResolvedRagChunk> chunks = resolveChunks(request.objectType(), request.objectId(), boundedLimit(request.limit()))
                .stream()
                .filter(chunk -> documentId == null || documentId.equals(chunk.documentId()))
                .toList();
        if (chunks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RAG chunks not found");
        }
        return ResponseEntity.ok(ApiResponse.ok(extractChunks(
                normalize(request.objectType()),
                normalize(request.objectId()),
                documentId,
                chunks.size(),
                chunks)));
    }

    @PostMapping("/rag-chunks")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage') "
            + "and @endpointAuthz.can('services:ai_rag','read') "
            + "and (@endpointAuthz.can('objects:' + #request.objectType().trim() + ':' + #request.objectId().trim(),'read') "
            + "or @endpointAuthz.can('objects:' + #request.objectType().trim(),'read'))")
    public ResponseEntity<ApiResponse<SkillRagBatchExtractionResponse>> extractRagChunks(
            @Valid @RequestBody SkillRagChunkExtractionRequest request) {
        String documentId = normalize(request.documentId());
        Map<String, String> requestedChunkIds = normalizedChunkIds(request.chunkIds());
        List<ResolvedRagChunk> resolved = resolveChunks(request.objectType(), request.objectId(), MAX_RAG_CHUNK_LIMIT);
        Map<String, ResolvedRagChunk> byChunkId = new LinkedHashMap<>();
        for (ResolvedRagChunk chunk : resolved) {
            if ((documentId == null || documentId.equals(chunk.documentId()))
                    && requestedChunkIds.containsKey(chunk.chunkId())) {
                byChunkId.putIfAbsent(chunk.chunkId(), chunk);
            }
        }
        if (byChunkId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RAG chunks not found");
        }
        List<ResolvedRagChunk> chunks = new ArrayList<>(byChunkId.values());
        SkillRagBatchExtractionResponse response = extractChunks(
                normalize(request.objectType()),
                normalize(request.objectId()),
                documentId,
                requestedChunkIds.size(),
                chunks);
        if (chunks.size() < requestedChunkIds.size()) {
            response = withMissingChunks(response, requestedChunkIds, byChunkId);
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private SkillRagBatchExtractionResponse extractChunks(
            String objectType,
            String objectId,
            String requestedDocumentId,
            int requestedChunks,
            List<ResolvedRagChunk> chunks) {
        List<SkillRagChunkExtractionItemDto> items = new ArrayList<>();
        int extractedCount = 0;
        int succeeded = 0;
        int failed = 0;
        for (ResolvedRagChunk chunk : chunks) {
            String sourceId = Optional.ofNullable(chunk.documentId()).orElse(objectId);
            try {
                SkillExtractionResult result = extractionService.extract(new SkillExtractionCommand(
                        RAG_CHUNK_SOURCE_TYPE,
                        sourceId,
                        chunk.chunkId(),
                        chunk.content()));
                extractedCount += result.extractedCount();
                succeeded++;
                items.add(new SkillRagChunkExtractionItemDto(
                        chunk.chunkId(),
                        chunk.documentId(),
                        sourceId,
                        result.sourceChunkId(),
                        result.extractedCount(),
                        "SUCCEEDED",
                        null));
            } catch (RuntimeException ex) {
                failed++;
                items.add(new SkillRagChunkExtractionItemDto(
                        chunk.chunkId(),
                        chunk.documentId(),
                        sourceId,
                        null,
                        0,
                        "FAILED",
                        ex.getMessage()));
            }
        }
        return new SkillRagBatchExtractionResponse(
                objectType,
                objectId,
                requestedDocumentId,
                requestedChunks,
                chunks.size(),
                succeeded,
                failed,
                extractedCount,
                items);
    }

    private SkillRagBatchExtractionResponse withMissingChunks(
            SkillRagBatchExtractionResponse response,
            Map<String, String> requestedChunkIds,
            Map<String, ResolvedRagChunk> resolvedByChunkId) {
        List<SkillRagChunkExtractionItemDto> items = new ArrayList<>(response.items());
        int missing = 0;
        for (Map.Entry<String, String> entry : requestedChunkIds.entrySet()) {
            if (resolvedByChunkId.containsKey(entry.getKey())) {
                continue;
            }
            missing++;
            items.add(new SkillRagChunkExtractionItemDto(
                    entry.getValue(),
                    response.documentId(),
                    null,
                    null,
                    0,
                    "NOT_FOUND",
                    "RAG chunk not found"));
        }
        return new SkillRagBatchExtractionResponse(
                response.objectType(),
                response.objectId(),
                response.documentId(),
                response.requestedChunks(),
                response.resolvedChunks(),
                response.succeededChunks(),
                response.failedChunks() + missing,
                response.extractedCount(),
                items);
    }

    private List<ResolvedRagChunk> resolveChunks(String objectType, String objectId, int limit) {
        SkillGraphRagChunkResolver resolver = ragChunkResolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RAG chunk resolver is not configured");
        }
        try {
            return resolver.listByObject(normalizeRequired(objectType, "objectType"),
                    normalizeRequired(objectId, "objectId"), limit)
                    .stream()
                    .filter(chunk -> chunk.content() != null && !chunk.content().isBlank())
                    .toList();
        } catch (UnsupportedOperationException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "RAG chunk resolve is not supported", ex);
        }
    }

    private Map<String, String> normalizedChunkIds(List<String> chunkIds) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String chunkId : chunkIds) {
            String value = normalizeRequired(chunkId, "chunkId");
            normalized.putIfAbsent(value, value);
        }
        return normalized;
    }

    private int boundedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RAG_CHUNK_LIMIT;
        }
        return Math.min(limit, MAX_RAG_CHUNK_LIMIT);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return normalized;
    }

}
