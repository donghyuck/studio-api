package studio.one.platform.skillgraph.web.controller;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
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

import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.web.dto.response.SkillRagChunkPreviewDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.extraction-source-base-path:/api/mgmt/skillgraph/extraction-sources}")
@Validated
public class SkillGraphExtractionSourceMgmtController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int PREVIEW_LENGTH = 240;
    private static final String LEGACY_GENERIC_ATTACHMENT_OBJECT_TYPE = "2001";
    private static final String ATTACHMENT_OBJECT_TYPE = "attachment";

    private final ObjectProvider<SkillGraphRagChunkResolver> ragChunkResolverProvider;
    private final ObjectProvider<ObjectTypeRuntimeService> objectTypeRuntimeServiceProvider;

    public SkillGraphExtractionSourceMgmtController(
            ObjectProvider<SkillGraphRagChunkResolver> ragChunkResolverProvider) {
        this(ragChunkResolverProvider, null);
    }

    @Autowired
    public SkillGraphExtractionSourceMgmtController(
            ObjectProvider<SkillGraphRagChunkResolver> ragChunkResolverProvider,
            ObjectProvider<ObjectTypeRuntimeService> objectTypeRuntimeServiceProvider) {
        this.ragChunkResolverProvider = Objects.requireNonNull(ragChunkResolverProvider, "ragChunkResolverProvider");
        this.objectTypeRuntimeServiceProvider = objectTypeRuntimeServiceProvider;
    }

    @GetMapping("/rag/chunks")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read') "
            + "and @endpointAuthz.can('services:ai_rag','read') "
            + "and @endpointAuthz.can('objects:' + #objectType.trim(),'read')")
    public ResponseEntity<ApiResponse<Page<SkillRagChunkPreviewDto>>> ragChunks(
            @RequestParam("objectType") String objectType,
            @RequestParam(name = "objectId", required = false) String objectId,
            @RequestParam(name = "documentId", required = false) String documentId,
            @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = DEFAULT_LIMIT, sort = "objectId") Pageable pageable) {
        SkillGraphRagChunkResolver resolver = requireResolver();
        String normalizedObjectType = normalizeRagObjectType(objectType);
        String normalizedObjectId = normalize(objectId);
        String normalizedDocumentId = normalize(documentId);
        String query = normalize(q);
        Pageable boundedPageable = boundedPageable(pageable);
        Page<ResolvedRagChunk> chunks;
        if (query != null || normalizedDocumentId != null) {
            chunks = resolver.pageByObject(
                    normalizedObjectType,
                    normalizedObjectId,
                    normalizedDocumentId,
                    query,
                    boundedPageable);
        } else {
            chunks = resolver.pageByObject(normalizedObjectType, normalizedObjectId, boundedPageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(chunks.map(this::toPreview)));
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
                chunk.objectId(),
                chunk.chunkOrder(),
                chunk.page(),
                chunk.section(),
                preview(content),
                chunk.tokenCount(),
                content.length(),
                chunk.warningStatus());
    }

    private int boundedLimit(int limit) {
        int requested = limit <= 0 ? DEFAULT_LIMIT : limit;
        return Math.min(requested, MAX_LIMIT);
    }

    private Pageable boundedPageable(Pageable pageable) {
        Pageable requested = pageable == null ? PageRequest.of(0, DEFAULT_LIMIT) : pageable;
        int boundedSize = boundedLimit(requested.getPageSize());
        return PageRequest.of(Math.max(0, requested.getPageNumber()), boundedSize, requested.getSort());
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

    private String normalizeRagObjectType(String objectType) {
        String normalized = required(objectType, "objectType");
        normalized = resolveObjectTypeCode(normalized);
        return LEGACY_GENERIC_ATTACHMENT_OBJECT_TYPE.equals(normalized) ? ATTACHMENT_OBJECT_TYPE : normalized;
    }

    private String resolveObjectTypeCode(String objectType) {
        if (objectTypeRuntimeServiceProvider == null) {
            return objectType;
        }
        ObjectTypeRuntimeService service = objectTypeRuntimeServiceProvider.getIfAvailable();
        if (service == null) {
            return objectType;
        }
        try {
            if (isInteger(objectType)) {
                ObjectTypeDefinition definition = service.definition(Integer.parseInt(objectType));
                if (definition == null || definition.type() == null) {
                    return objectType;
                }
                String code = normalize(definition.type().code());
                return code == null ? objectType : code;
            }
            return objectType;
        } catch (RuntimeException ex) {
            return objectType;
        }
    }

    private boolean isInteger(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
