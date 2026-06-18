package studio.one.platform.ai.service.visualization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.vector.VectorSearchHit;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResults;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointView;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;

@Slf4j
public class DefaultVectorSearchVisualizationService implements VectorSearchVisualizationService {

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final VectorProjectionRepository projectionRepository;
    private final VectorProjectionPointRepository pointRepository;
    private final ExistingVectorItemRepository itemRepository;
    private final AiProviderRegistry providerRegistry;

    public DefaultVectorSearchVisualizationService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository) {
        this(embeddingPort, vectorStorePort, projectionRepository, pointRepository, itemRepository, null);
    }

    public DefaultVectorSearchVisualizationService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            AiProviderRegistry providerRegistry) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.vectorStorePort = Objects.requireNonNull(vectorStorePort, "vectorStorePort");
        this.projectionRepository = Objects.requireNonNull(projectionRepository, "projectionRepository");
        this.pointRepository = Objects.requireNonNull(pointRepository, "pointRepository");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository");
        this.providerRegistry = providerRegistry;
    }

    @Override
    public VectorSearchVisualizationResult search(VectorSearchVisualizationCommand command) {
        String query = normalize(command.query());
        if (query == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        VectorProjection projection = projectionRepository.findById(command.projectionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PROJECTION_NOT_FOUND"));
        if (projection.status() != ProjectionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PROJECTION_NOT_READY");
        }
        long embedStart = System.nanoTime();
        List<Double> embedding = embed(query, command.projectionId(), command.embeddingProvider(), command.embeddingModel());
        long embedElapsedMs = elapsedMs(embedStart);
        int topK = effectiveTopK(command.topK());
        List<String> effectiveTargetTypes = effectiveTargetTypes(projection.targetTypes(), command.targetTypes());
        if (!normalizedDistinct(projection.targetTypes()).isEmpty()
                && !normalizedDistinct(command.targetTypes()).isEmpty()
                && effectiveTargetTypes.isEmpty()) {
            return new VectorSearchVisualizationResult(
                    new VectorSearchVisualizationResult.QueryPoint(query, null, null),
                    List.of());
        }
        long searchStart = System.nanoTime();
        List<VectorSearchHit> hits = searchHits(
                command.projectionId(),
                query,
                embedding,
                effectiveTargetTypes,
                projection.filters(),
                topK,
                command.minScore());
        long searchElapsedMs = elapsedMs(searchStart);
        Map<String, Double> similarityById = new LinkedHashMap<>();
        List<String> vectorItemIds = new ArrayList<>();
        for (VectorSearchHit hit : hits) {
            String vectorItemId = vectorItemId(hit);
            if (vectorItemId != null && !similarityById.containsKey(vectorItemId)) {
                similarityById.put(vectorItemId, hit.score());
                vectorItemIds.add(vectorItemId);
            }
        }
        Map<String, ProjectionPointView> pointById = new LinkedHashMap<>();
        long pointStart = System.nanoTime();
        for (ProjectionPointView point : pointRepository.findByVectorItemIds(projection.projectionId(), vectorItemIds)) {
            pointById.put(point.vectorItemId(), point);
        }
        long pointElapsedMs = elapsedMs(pointStart);
        List<VectorSearchVisualizationResult.ResultPoint> results = new ArrayList<>();
        for (String vectorItemId : vectorItemIds) {
            ProjectionPointView point = pointById.get(vectorItemId);
            if (point == null) {
                continue;
            }
            results.add(new VectorSearchVisualizationResult.ResultPoint(
                    point.vectorItemId(),
                    point.targetType(),
                    point.sourceId(),
                    point.label(),
                    point.x(),
                    point.y(),
                    similarityById.get(vectorItemId)));
        }
        Double x = null;
        Double y = null;
        if (!results.isEmpty()) {
            x = results.stream().mapToDouble(VectorSearchVisualizationResult.ResultPoint::x).average().orElse(0.0d);
            y = results.stream().mapToDouble(VectorSearchVisualizationResult.ResultPoint::y).average().orElse(0.0d);
        }
        long totalElapsedMs = embedElapsedMs + searchElapsedMs + pointElapsedMs;
        log.info("Vector search visualization completed. projectionId={}, topK={}, hits={}, matchedPoints={}, "
                        + "embedElapsedMs={}, searchElapsedMs={}, pointElapsedMs={}, measuredElapsedMs={}",
                command.projectionId(), topK, hits.size(), results.size(),
                embedElapsedMs, searchElapsedMs, pointElapsedMs, totalElapsedMs);
        return new VectorSearchVisualizationResult(
                new VectorSearchVisualizationResult.QueryPoint(query, x, y),
                results);
    }

    private List<Double> embed(String query, String projectionId, String requestedProvider, String requestedModel) {
        String provider = normalize(requestedProvider);
        String model = normalize(requestedModel);
        if (provider == null || model == null) {
            try {
                ProjectionPointPage page = pointRepository.findPage(projectionId, null, null, null, 1, 0);
                if (page != null && !page.items().isEmpty()) {
                    String itemId = page.items().get(0).vectorItemId();
                    java.util.Optional<VectorItem> itemOpt = itemRepository.findByVectorItemId(itemId);
                    if (itemOpt.isPresent()) {
                        VectorItem item = itemOpt.get();
                        if (model == null) {
                            model = item.embeddingModel();
                        }
                        if (provider == null) {
                            Object providerVal = item.metadata().get("embeddingProvider");
                            if (providerVal != null) {
                                provider = normalize(providerVal.toString());
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to resolve embedding model/provider for projection: {}", projectionId, ex);
            }
        }

        try {
            EmbeddingResponse response = embeddingPort(provider).embed(new EmbeddingRequest(
                    List.of(query),
                    provider,
                    model,
                    EmbeddingInputType.TEXT,
                    Map.of()
            ));
            return response.vectors().get(0).values();
        } catch (RuntimeException ex) {
            log.warn("Vector search embedding failed. projectionId={}, provider={}, model={}",
                    projectionId, provider, model, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "EMBEDDING_FAILED", ex);
        }
    }

    private EmbeddingPort embeddingPort(String provider) {
        if (providerRegistry == null) {
            return embeddingPort;
        }
        return providerRegistry.embeddingPort(provider);
    }

    private List<VectorSearchHit> searchHits(
            String projectionId,
            String query,
            List<Double> embedding,
            List<String> targetTypes,
            Map<String, Object> filters,
            int topK,
            Double minScore) {
        VectorSearchRequest baseRequest = new VectorSearchRequest(
                embedding,
                query,
                topK,
                searchFilter(filters, targetTypes),
                minScore,
                false,
                true);
        try {
            return vectorStorePort.searchWithFilter(baseRequest).hits().stream()
                    .filter(hit -> minScore == null || hit.score() >= minScore)
                    .limit(topK)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Vector search failed. projectionId={}, queryLength={}, embeddingDimension={}, targetTypes={}, "
                            + "filterKeys={}, topK={}, minScore={}",
                    projectionId,
                    query.length(),
                    embedding.size(),
                    targetTypes == null ? List.of() : targetTypes,
                    filters == null ? List.of() : filters.keySet(),
                    topK,
                    minScore,
                    ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECTION_SEARCH_FAILED", ex);
        }
    }

    private MetadataFilter searchFilter(Map<String, Object> filters, List<String> targetTypes) {
        List<String> normalizedTargetTypes = normalizedDistinct(targetTypes);
        Map<String, List<Object>> inCriteria = normalizedTargetTypes.isEmpty()
                ? Map.of()
                : Map.of("objectType", List.copyOf(normalizedTargetTypes));
        return MetadataFilter.of(filters, inCriteria, Map.of());
    }

    private String vectorItemId(VectorSearchHit hit) {
        Object chunkId = hit.metadata().get("chunkId");
        if (chunkId != null && !chunkId.toString().isBlank()) {
            return chunkId.toString();
        }
        Object rowId = hit.metadata().get("_vectorRowId");
        if (rowId != null && !rowId.toString().isBlank()) {
            return rowId.toString();
        }
        Object documentId = hit.metadata().get("documentId");
        if (documentId != null && !documentId.toString().isBlank()) {
            return documentId.toString();
        }
        return hit.id();
    }

    private List<String> effectiveTargetTypes(List<String> projectionTargetTypes, List<String> requestedTargetTypes) {
        List<String> projectionTypes = normalizedDistinct(projectionTargetTypes);
        List<String> requestedTypes = normalizedDistinct(requestedTargetTypes);
        if (projectionTypes.isEmpty()) {
            return requestedTypes;
        }
        if (requestedTypes.isEmpty()) {
            return projectionTypes;
        }
        return requestedTypes.stream()
                .filter(projectionTypes::contains)
                .toList();
    }

    private List<String> normalizedDistinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private int effectiveTopK(Integer topK) {
        if (topK == null) {
            return 10;
        }
        return Math.max(1, Math.min(topK, VectorSearchRequest.MAX_TOP_K));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
