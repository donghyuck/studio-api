package studio.one.platform.skillgraph.web.controller;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.web.dto.response.SkillRagChunkPreviewDto;
import studio.one.platform.web.dto.ApiResponse;
import studio.one.platform.web.dto.PageDto;

@RestController
@RequestMapping("${studio.features.skillgraph.web.extraction-source-base-path:/api/mgmt/skillgraph/extraction-sources}")
@Validated
public class SkillGraphExtractionSourceMgmtController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int PREVIEW_LENGTH = 240;

    private final ObjectProvider<SkillGraphRagChunkResolver> ragChunkResolverProvider;

    public SkillGraphExtractionSourceMgmtController(
            ObjectProvider<SkillGraphRagChunkResolver> ragChunkResolverProvider) {
        this.ragChunkResolverProvider = Objects.requireNonNull(ragChunkResolverProvider, "ragChunkResolverProvider");
    }

    @GetMapping("/rag/chunks")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read') "
            + "and @endpointAuthz.can('services:ai_rag','read') "
            + "and (@endpointAuthz.can('objects:' + #objectType.trim() + ':' + #objectId.trim(),'read') "
            + "or @endpointAuthz.can('objects:' + #objectType.trim(),'read'))")
    public ResponseEntity<ApiResponse<PageDto<SkillRagChunkPreviewDto>>> ragChunks(
            @RequestParam("objectType") String objectType,
            @RequestParam("objectId") String objectId,
            @RequestParam(name = "documentId", required = false) String documentId,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "limit", required = false) Integer limit,
            @PageableDefault(size = DEFAULT_LIMIT) Pageable pageable,
            @RequestParam(name = "sort", required = false) String sort) {
        SkillGraphRagChunkResolver resolver = requireResolver();
        String normalizedObjectType = required(objectType, "objectType");
        String normalizedObjectId = required(objectId, "objectId");
        String normalizedDocumentId = normalize(documentId);
        String query = normalize(q);
        Pageable boundedPageable = boundedPageable(pageable, offset, limit);
        int boundedOffset = pageOffset(boundedPageable);
        int boundedLimit = boundedPageable.getPageSize();
        List<ResolvedRagChunk> filtered;
        long total;
        if (query != null || normalizedDocumentId != null) {
            filtered = resolver.listByObject(
                    normalizedObjectType,
                    normalizedObjectId,
                    normalizedDocumentId,
                    query,
                    boundedOffset,
                    boundedLimit).stream()
                    .sorted((left, right) -> compare(left, right, sort))
                    .toList();
            total = resolver.countByObject(normalizedObjectType, normalizedObjectId, normalizedDocumentId, query);
        } else {
            filtered = resolver.listByObject(
                    normalizedObjectType,
                    normalizedObjectId,
                    boundedOffset,
                    boundedLimit).stream()
                    .sorted((left, right) -> compare(left, right, sort))
                    .toList();
            total = resolver.countByObject(normalizedObjectType, normalizedObjectId);
        }
        if (boundedOffset == 0 && filtered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RAG chunks not found");
        }
        List<SkillRagChunkPreviewDto> items = filtered.stream()
                .map(this::toPreview)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(PageDto.from(new PageImpl<>(items, boundedPageable, total))));
    }

    private SkillGraphRagChunkResolver requireResolver() {
        SkillGraphRagChunkResolver resolver = ragChunkResolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RAG chunk resolver is not configured");
        }
        return resolver;
    }

    private SkillRagChunkPreviewDto toPreview(ResolvedRagChunk chunk) {
        String content = chunk.content() == null ? "" : chunk.content();
        return new SkillRagChunkPreviewDto(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.chunkOrder(),
                chunk.page(),
                chunk.section(),
                preview(content),
                chunk.tokenCount(),
                content.length(),
                chunk.warningStatus());
    }

    private int compare(ResolvedRagChunk left, ResolvedRagChunk right, String sort) {
        if ("chunkOrderDesc".equalsIgnoreCase(sort)) {
            return Integer.compare(order(right), order(left));
        }
        return Integer.compare(order(left), order(right));
    }

    private int order(ResolvedRagChunk chunk) {
        return chunk.chunkOrder() == null ? Integer.MAX_VALUE : chunk.chunkOrder();
    }

    private int boundedLimit(int limit) {
        int requested = limit <= 0 ? DEFAULT_LIMIT : limit;
        return Math.min(requested, MAX_LIMIT);
    }

    private Pageable boundedPageable(Pageable pageable, Integer offset, Integer limit) {
        if (offset != null || limit != null) {
            int boundedLimit = boundedLimit(limit == null ? DEFAULT_LIMIT : limit);
            int boundedOffset = Math.max(0, offset == null ? 0 : offset);
            return PageRequest.of(boundedOffset / boundedLimit, boundedLimit, pageable.getSort());
        }
        Pageable requested = pageable == null ? PageRequest.of(0, DEFAULT_LIMIT) : pageable;
        int boundedSize = boundedLimit(requested.getPageSize());
        return PageRequest.of(Math.max(0, requested.getPageNumber()), boundedSize, requested.getSort());
    }

    private int pageOffset(Pageable pageable) {
        long offset = pageable.getOffset();
        return offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset;
    }

    private String preview(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= PREVIEW_LENGTH ? normalized : normalized.substring(0, PREVIEW_LENGTH);
    }

    private String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
