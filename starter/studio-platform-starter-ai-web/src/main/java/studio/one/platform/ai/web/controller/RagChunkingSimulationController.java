package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.web.dto.RagChunkingSimulationRequestDto;
import studio.one.platform.ai.web.dto.RagChunkingSimulationResponseDto;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag/simulations")
@Validated
public class RagChunkingSimulationController {

    @Nullable
    private final ChunkingOrchestrator chunkingOrchestrator;
    private final AiWebRagProperties ragProperties;

    public RagChunkingSimulationController(
            @Nullable ChunkingOrchestrator chunkingOrchestrator,
            AiWebRagProperties ragProperties) {
        this.chunkingOrchestrator = chunkingOrchestrator;
        this.ragProperties = Objects.requireNonNull(ragProperties, "ragProperties");
    }

    @PostMapping("/chunking")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagChunkingSimulationResponseDto>> simulateChunking(
            @Valid @RequestBody RagChunkingSimulationRequestDto request) {
        if (chunkingOrchestrator == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ChunkingOrchestrator is not configured");
        }
        if (!hasText(request.text())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
        if (request.text().length() > ragProperties.getChunkPreview().getMaxInputChars()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "text exceeds max chunking simulation input length");
        }
        try {
            ChunkingContext context = toContext(request);
            List<Chunk> chunks = chunkingOrchestrator.chunk(context);
            int maxPreviewChunks = ragProperties.getChunkPreview().getMaxPreviewChunks();
            List<String> warnings = new ArrayList<>();
            if (chunks.size() > maxPreviewChunks) {
                warnings.add("Chunking simulation truncated to " + maxPreviewChunks + " chunks.");
            }
            List<Chunk> returned = chunks.size() > maxPreviewChunks ? chunks.subList(0, maxPreviewChunks) : chunks;
            List<RagChunkingSimulationResponseDto.ChunkDto> chunkDtos = returned.stream()
                    .map(chunk -> toChunkDto(chunk, request.maxChunkSize()))
                    .toList();
            warnings.addAll(tokenizerWarnings(returned));
            warnings.addAll(maxChunkSizeWarnings(returned, request.maxChunkSize()));
            int totalTokens = chunks.stream().mapToInt(this::tokenCount).sum();
            return ResponseEntity.ok(ApiResponse.ok(new RagChunkingSimulationResponseDto(
                    tokenizerStatus(request, context, returned),
                    chunkDtos,
                    tokenDistribution(chunks),
                    chunks.size(),
                    chunks.stream().mapToInt(chunk -> chunk.content().length()).sum(),
                    totalTokens,
                    warnings.stream().distinct().toList())));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Chunking simulation failed: " + ex.getMessage(), ex);
        }
    }

    private ChunkingContext toContext(RagChunkingSimulationRequestDto request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, VectorRecord.KEY_OBJECT_TYPE, request.objectType());
        put(metadata, VectorRecord.KEY_OBJECT_ID, request.objectId());
        put(metadata, "attachmentId", request.attachmentId());
        put(metadata, VectorRecord.KEY_EMBEDDING_PROVIDER, request.embeddingProvider());
        put(metadata, VectorRecord.KEY_EMBEDDING_MODEL, request.embeddingModel());
        if (request.tokenizerAutoDetect() != null) {
            metadata.put("tokenizerAutoDetect", request.tokenizerAutoDetect());
        }
        ChunkingContext.Builder builder = ChunkingContext.configuredDefaults(request.text())
                .objectType(text(request.objectType()))
                .objectId(text(request.objectId()))
                .metadata(metadata)
                .unit(ChunkUnit.from(request.chunkUnit()));
        if (request.chunkSize() != null) {
            builder.maxSize(request.chunkSize());
        }
        if (request.chunkOverlap() != null) {
            builder.overlap(request.chunkOverlap());
        }
        return builder.build();
    }

    private RagChunkingSimulationResponseDto.TokenizerStatusDto tokenizerStatus(
            RagChunkingSimulationRequestDto request,
            ChunkingContext context,
            List<Chunk> chunks) {
        Map<String, Object> metadata = mergedMetadata(chunks);
        return new RagChunkingSimulationResponseDto.TokenizerStatusDto(
                request.embeddingProvider(),
                request.embeddingModel(),
                text(metadata.get(ChunkMetadata.KEY_TOKENIZER_PROVIDER)),
                text(metadata.get(ChunkMetadata.KEY_TOKENIZER_ENCODING)),
                text(metadata.get(ChunkMetadata.KEY_TOKENIZER_SELECTION_SOURCE)),
                text(metadata.get(ChunkMetadata.KEY_TOKENIZER_CONFIDENCE)),
                context.unit().value(),
                effectiveSize(context.maxSize()),
                effectiveOverlap(context.overlap()),
                bool(metadata.get(ChunkMetadata.KEY_TOKENIZER_FALLBACK_USED)),
                tokenizerWarnings(chunks));
    }

    private RagChunkingSimulationResponseDto.ChunkDto toChunkDto(Chunk chunk, Integer maxChunkSize) {
        Map<String, Object> metadata = chunk.metadata().toMap();
        int tokenCount = tokenCount(chunk);
        List<String> warnings = new ArrayList<>();
        if (maxChunkSize != null && maxChunkSize > 0 && tokenCount > maxChunkSize) {
            warnings.add("chunkTokenCount exceeds maxChunkSize: " + tokenCount + " > " + maxChunkSize);
        }
        warnings.addAll(warnings(metadata.get(ChunkMetadata.KEY_TOKENIZER_WARNINGS)));
        return new RagChunkingSimulationResponseDto.ChunkDto(
                chunk.id(),
                chunk.content(),
                tokenCount,
                chunk.content().length(),
                text(metadata.get(ChunkMetadata.KEY_TOKENIZER_PROVIDER)),
                text(metadata.get(ChunkMetadata.KEY_TOKENIZER_ENCODING)),
                text(metadata.get(VectorRecord.KEY_EMBEDDING_MODEL)),
                text(metadata.get(ChunkMetadata.KEY_CHUNK_TYPE)),
                integer(metadata.get(ChunkMetadata.KEY_PAGE)),
                text(firstPresent(metadata, ChunkMetadata.KEY_HEADING_PATH, ChunkMetadata.KEY_SECTION)),
                warnings.stream().distinct().toList());
    }

    private RagChunkingSimulationResponseDto.TokenDistributionDto tokenDistribution(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return new RagChunkingSimulationResponseDto.TokenDistributionDto(0, 0, 0.0d);
        }
        List<Integer> counts = chunks.stream().map(this::tokenCount).toList();
        int min = counts.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = counts.stream().mapToInt(Integer::intValue).max().orElse(0);
        double average = counts.stream().mapToInt(Integer::intValue).average().orElse(0.0d);
        return new RagChunkingSimulationResponseDto.TokenDistributionDto(min, max, average);
    }

    private List<String> maxChunkSizeWarnings(List<Chunk> chunks, Integer maxChunkSize) {
        if (maxChunkSize == null || maxChunkSize <= 0) {
            return List.of();
        }
        return chunks.stream()
                .filter(chunk -> tokenCount(chunk) > maxChunkSize)
                .map(chunk -> "Chunk " + chunk.id() + " exceeds maxChunkSize: "
                        + tokenCount(chunk) + " > " + maxChunkSize)
                .toList();
    }

    private List<String> tokenizerWarnings(List<Chunk> chunks) {
        return chunks.stream()
                .flatMap(chunk -> warnings(chunk.metadata().toMap().get(ChunkMetadata.KEY_TOKENIZER_WARNINGS)).stream())
                .distinct()
                .toList();
    }

    private Map<String, Object> mergedMetadata(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return Map.of();
        }
        return chunks.get(0).metadata().toMap();
    }

    private int tokenCount(Chunk chunk) {
        Map<String, Object> metadata = chunk.metadata().toMap();
        Integer tokenCount = integer(firstPresent(metadata,
                ChunkMetadata.KEY_CHUNK_TOKEN_COUNT,
                ChunkMetadata.KEY_TOKEN_COUNT,
                ChunkMetadata.KEY_TOKEN_ESTIMATE));
        if (tokenCount != null) {
            return tokenCount;
        }
        return Math.max(1, (int) Math.ceil(chunk.content().replaceAll("\\s+", " ").trim().length() / 4.0d));
    }

    private int effectiveSize(int value) {
        return value == ChunkingContext.USE_CONFIGURED_MAX_SIZE ? ChunkingContext.DEFAULT_MAX_SIZE : value;
    }

    private int effectiveOverlap(int value) {
        return value == ChunkingContext.USE_CONFIGURED_OVERLAP ? ChunkingContext.DEFAULT_OVERLAP : value;
    }

    private void put(Map<String, Object> metadata, String key, String value) {
        String text = text(value);
        if (text != null) {
            metadata.put(key, text);
        }
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

    private List<String> warnings(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            iterable.forEach(item -> {
                String text = text(item);
                if (text != null) {
                    values.add(text);
                }
            });
            return values;
        }
        String text = text(value);
        return text == null ? List.of() : List.of(text);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
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
