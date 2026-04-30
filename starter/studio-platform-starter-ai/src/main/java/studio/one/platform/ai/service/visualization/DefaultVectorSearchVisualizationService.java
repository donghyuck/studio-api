package studio.one.platform.ai.service.visualization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.vector.VectorSearchHit;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResults;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointView;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;

public class DefaultVectorSearchVisualizationService implements VectorSearchVisualizationService {

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final VectorProjectionRepository projectionRepository;
    private final VectorProjectionPointRepository pointRepository;

    public DefaultVectorSearchVisualizationService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.vectorStorePort = Objects.requireNonNull(vectorStorePort, "vectorStorePort");
        this.projectionRepository = Objects.requireNonNull(projectionRepository, "projectionRepository");
        this.pointRepository = Objects.requireNonNull(pointRepository, "pointRepository");
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
        List<Double> embedding = embed(query);
        int topK = effectiveTopK(command.topK());
        List<String> effectiveTargetTypes = effectiveTargetTypes(projection.targetTypes(), command.targetTypes());
        if (!normalizedDistinct(projection.targetTypes()).isEmpty()
                && !normalizedDistinct(command.targetTypes()).isEmpty()
                && effectiveTargetTypes.isEmpty()) {
            return new VectorSearchVisualizationResult(
                    new VectorSearchVisualizationResult.QueryPoint(query, null, null),
                    List.of());
        }
        List<VectorSearchHit> hits = searchHits(
                query,
                embedding,
                effectiveTargetTypes,
                projection.filters(),
                topK,
                command.minScore());
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
        for (ProjectionPointView point : pointRepository.findByVectorItemIds(projection.projectionId(), vectorItemIds)) {
            pointById.put(point.vectorItemId(), point);
        }
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
        return new VectorSearchVisualizationResult(
                new VectorSearchVisualizationResult.QueryPoint(query, x, y),
                results);
    }

    private List<Double> embed(String query) {
        try {
            EmbeddingResponse response = embeddingPort.embed(new EmbeddingRequest(List.of(query)));
            return response.vectors().get(0).values();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "EMBEDDING_FAILED", ex);
        }
    }

    private List<VectorSearchHit> searchHits(
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
                MetadataFilter.of(filters, Map.of(), Map.of()),
                minScore,
                false,
                true);
        try {
            if (targetTypes == null || targetTypes.isEmpty()) {
                return vectorStorePort.searchWithFilter(baseRequest).hits().stream()
                        .filter(hit -> minScore == null || hit.score() >= minScore)
                        .limit(topK)
                        .toList();
            }
            return targetTypes.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .flatMap(targetType -> vectorStorePort.searchByObject(targetType.trim(), null, baseRequest).stream())
                    .map(result -> VectorSearchHit.from(result, false, true))
                    .filter(hit -> minScore == null || hit.score() >= minScore)
                    .sorted(Comparator.comparingDouble(VectorSearchHit::score).reversed())
                    .limit(topK)
                    .toList();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECTION_SEARCH_FAILED", ex);
        }
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
}
