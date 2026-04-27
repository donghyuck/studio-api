package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.Valid;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.autoconfigure.config.RagPipelineProperties;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.web.dto.RagChunkConfigResponseDto;
import studio.one.platform.ai.web.dto.RagChunkPreviewItemDto;
import studio.one.platform.ai.web.dto.RagChunkPreviewRequestDto;
import studio.one.platform.ai.web.dto.RagChunkPreviewResponseDto;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag/chunks")
@Validated
@SuppressWarnings("deprecation")
public class RagChunkPreviewController {

    private static final List<String> AVAILABLE_STRATEGIES = List.of("fixed-size", "recursive", "structure-based");
    private static final String CHUNKING_PREFIX = "studio.chunking";

    @Nullable
    private final ChunkingOrchestrator chunkingOrchestrator;
    private final List<Chunker> chunkers;
    @Nullable
    private final TextChunker textChunker;
    private final RagPipelineProperties ragPipelineProperties;
    private final AiWebRagProperties ragProperties;
    private final Environment environment;

    public RagChunkPreviewController(
            @Nullable ChunkingOrchestrator chunkingOrchestrator,
            List<Chunker> chunkers,
            @Nullable TextChunker textChunker,
            RagPipelineProperties ragPipelineProperties,
            AiWebRagProperties ragProperties,
            Environment environment) {
        this.chunkingOrchestrator = chunkingOrchestrator;
        this.chunkers = chunkers == null ? List.of() : List.copyOf(chunkers);
        this.textChunker = textChunker;
        this.ragPipelineProperties = Objects.requireNonNull(ragPipelineProperties, "ragPipelineProperties");
        this.ragProperties = Objects.requireNonNull(ragProperties, "ragProperties");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    @PostMapping("/preview")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagChunkPreviewResponseDto>> preview(
            @Valid @RequestBody RagChunkPreviewRequestDto request) {
        if (!ragProperties.getChunkPreview().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chunk preview is disabled");
        }
        if (chunkingOrchestrator == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ChunkingOrchestrator is not configured");
        }
        if (!hasText(request.text())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
        if (request.text().length() > ragProperties.getChunkPreview().getMaxInputChars()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "text exceeds max chunk preview input length");
        }
        try {
            Map<String, Object> requestMetadata = sanitizedMetadata(request.metadata());
            ChunkingContext context = toContext(request);
            List<Chunk> chunks = chunkingOrchestrator.chunk(context);
            int totalChars = chunks.stream().mapToInt(chunk -> chunk.content().length()).sum();
            int maxPreviewChunks = ragProperties.getChunkPreview().getMaxPreviewChunks();
            List<String> warnings = new ArrayList<>();
            List<Chunk> returned = chunks;
            if (chunks.size() > maxPreviewChunks) {
                returned = chunks.subList(0, maxPreviewChunks);
                warnings.add("Chunk preview truncated to " + maxPreviewChunks + " chunks.");
            }
            return ResponseEntity.ok(ApiResponse.ok(new RagChunkPreviewResponseDto(
                    returned.stream().map(chunk -> toItem(chunk, requestMetadata)).toList(),
                    chunks.size(),
                    totalChars,
                    effectiveStrategy(context).value(),
                    effectiveMaxSize(context),
                    effectiveOverlap(context),
                    context.unit().value(),
                    warnings)));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/config")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagChunkConfigResponseDto>> config() {
        AiWebRagProperties.ContextProperties context = ragProperties.getContext();
        AiWebRagProperties.ExpansionProperties expansion = context.getExpansion();
        String configuredStrategy = environment.getProperty(CHUNKING_PREFIX + ".strategy", "recursive");
        ChunkingStrategyType previewStrategy = previewStrategyOrNull(configuredStrategy);
        RagChunkConfigResponseDto response = new RagChunkConfigResponseDto(
                new RagChunkConfigResponseDto.ChunkingConfigDto(
                        chunkingOrchestrator != null,
                        environment.getProperty(CHUNKING_PREFIX + ".enabled", Boolean.class, true),
                        configuredStrategy,
                        previewStrategy == null ? null : previewStrategy.value(),
                        previewStrategy != null,
                        environment.getProperty(CHUNKING_PREFIX + ".max-size", Integer.class,
                                ChunkingContext.DEFAULT_MAX_SIZE),
                        environment.getProperty(CHUNKING_PREFIX + ".overlap", Integer.class,
                                ChunkingContext.DEFAULT_OVERLAP),
                        AVAILABLE_STRATEGIES,
                        registeredChunkers(),
                        chunkingOrchestrator != null),
                new RagChunkConfigResponseDto.LegacyFallbackConfigDto(
                        ragPipelineProperties.getChunkSize(),
                        ragPipelineProperties.getChunkOverlap(),
                        textChunker != null),
                new RagChunkConfigResponseDto.RagContextConfigDto(
                        context.getMaxChunks(),
                        context.getMaxChars(),
                        context.isIncludeScores(),
                        new RagChunkConfigResponseDto.RagContextExpansionConfigDto(
                                expansion.isEnabled(),
                                expansion.getCandidateMultiplier(),
                                expansion.getMaxCandidates(),
                                expansion.getPreviousWindow(),
                                expansion.getNextWindow(),
                                expansion.isIncludeParentContent())),
                new RagChunkConfigResponseDto.ChunkPreviewLimitsDto(
                        ragProperties.getChunkPreview().isEnabled(),
                        ragProperties.getChunkPreview().getMaxInputChars(),
                        ragProperties.getChunkPreview().getMaxPreviewChunks()));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private ChunkingContext toContext(RagChunkPreviewRequestDto request) {
        Map<String, Object> metadata = sanitizedMetadata(request.metadata());
        ChunkingContext.Builder builder = ChunkingContext.builder(request.text())
                .sourceDocumentId(text(request.documentId()))
                .objectType(text(request.objectType()))
                .objectId(text(request.objectId()))
                .contentType(text(request.contentType()))
                .filename(text(request.filename()))
                .metadata(metadata);
        if (!hasText(request.strategy())) {
            builder.strategy(configuredPreviewStrategy());
        } else {
            builder.strategy(supportedStrategy(request.strategy()));
        }
        if (request.maxSize() == null) {
            builder.useConfiguredMaxSize();
        } else {
            builder.maxSize(request.maxSize());
        }
        if (request.overlap() == null) {
            builder.useConfiguredOverlap();
        } else {
            builder.overlap(request.overlap());
        }
        builder.unit(ChunkUnit.from(request.unit()));
        return builder.build();
    }

    private ChunkingStrategyType supportedStrategy(String value) {
        ChunkingStrategyType strategy = ChunkingStrategyType.from(value);
        if (strategy != ChunkingStrategyType.FIXED_SIZE
                && strategy != ChunkingStrategyType.RECURSIVE
                && strategy != ChunkingStrategyType.STRUCTURE_BASED) {
            throw new IllegalArgumentException("Unsupported chunk preview strategy: " + value
                    + ". Supported values are: fixed-size, recursive, structure-based.");
        }
        return strategy;
    }

    private ChunkingStrategyType configuredPreviewStrategy() {
        String configuredStrategy = environment.getProperty(CHUNKING_PREFIX + ".strategy", "recursive");
        ChunkingStrategyType previewStrategy = previewStrategyOrNull(configuredStrategy);
        if (previewStrategy == null) {
            throw new IllegalArgumentException("Configured chunking strategy is not supported by chunk preview: "
                    + configuredStrategy + ". Supported values are: fixed-size, recursive, structure-based.");
        }
        return previewStrategy;
    }

    private ChunkingStrategyType previewStrategyOrNull(String value) {
        try {
            ChunkingStrategyType strategy = ChunkingStrategyType.from(value);
            if (strategy == ChunkingStrategyType.FIXED_SIZE
                    || strategy == ChunkingStrategyType.RECURSIVE
                    || strategy == ChunkingStrategyType.STRUCTURE_BASED) {
                return strategy;
            }
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private RagChunkPreviewItemDto toItem(Chunk chunk, Map<String, Object> requestMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>(requestMetadata);
        metadata.putAll(chunk.metadata().toMap());
        return new RagChunkPreviewItemDto(
                chunk.id(),
                chunk.content(),
                chunk.content().length(),
                integer(metadata.get(ChunkMetadata.KEY_CHUNK_ORDER)),
                text(metadata.get(ChunkMetadata.KEY_CHUNK_TYPE)),
                text(metadata.get(ChunkMetadata.KEY_PARENT_CHUNK_ID)),
                text(metadata.get(ChunkMetadata.KEY_PREVIOUS_CHUNK_ID)),
                text(metadata.get(ChunkMetadata.KEY_NEXT_CHUNK_ID)),
                text(metadata.get(ChunkMetadata.KEY_HEADING_PATH)),
                text(metadata.get(ChunkMetadata.KEY_SECTION)),
                text(firstPresent(metadata, ChunkMetadata.KEY_SOURCE_REF, ChunkMetadata.KEY_SOURCE_REFS)),
                integer(metadata.get(ChunkMetadata.KEY_PAGE)),
                integer(metadata.get(ChunkMetadata.KEY_SLIDE)),
                metadata);
    }

    private List<String> registeredChunkers() {
        return chunkers.stream()
                .map(chunker -> chunker.getClass().getSimpleName())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private ChunkingStrategyType effectiveStrategy(ChunkingContext context) {
        if (context.strategy() != null) {
            return context.strategy();
        }
        return ChunkingStrategyType.from(environment.getProperty(CHUNKING_PREFIX + ".strategy", "recursive"));
    }

    private int effectiveMaxSize(ChunkingContext context) {
        return context.maxSize() == ChunkingContext.USE_CONFIGURED_MAX_SIZE
                ? environment.getProperty(CHUNKING_PREFIX + ".max-size", Integer.class,
                        ChunkingContext.DEFAULT_MAX_SIZE)
                : context.maxSize();
    }

    private int effectiveOverlap(ChunkingContext context) {
        return context.overlap() == ChunkingContext.USE_CONFIGURED_OVERLAP
                ? environment.getProperty(CHUNKING_PREFIX + ".overlap", Integer.class,
                        ChunkingContext.DEFAULT_OVERLAP)
                : context.overlap();
    }

    private Map<String, Object> sanitizedMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            if (value instanceof String text && text.isBlank()) {
                return;
            }
            sanitized.put(key, value);
        });
        return sanitized;
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
}
