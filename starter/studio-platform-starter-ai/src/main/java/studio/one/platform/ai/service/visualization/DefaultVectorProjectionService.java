package studio.one.platform.ai.service.visualization;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.ProjectionMode;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionSamplingStrategy;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionScope;

public class DefaultVectorProjectionService implements VectorProjectionService {

    private static final int MAX_LIMIT = 5_000;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_TARGET_TYPES_LENGTH = 500;
    private static final Pattern FILTER_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");

    private final VectorProjectionRepository projectionRepository;
    private final VectorProjectionPointRepository pointRepository;
    private final ExistingVectorItemRepository itemRepository;
    private final VectorProjectionJobService jobService;
    private final Executor executor;
    private final int maxItems;
    private final int defaultSampleSize;
    private final ProjectionSamplingStrategy defaultSamplingStrategy;
    private final Duration processingTimeout;

    public DefaultVectorProjectionService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            VectorProjectionJobService jobService,
            Executor executor) {
        this(projectionRepository, pointRepository, itemRepository, jobService, executor,
                ExistingVectorItemRepository.DEFAULT_MAX_PROJECTION_ITEMS,
                ExistingVectorItemRepository.DEFAULT_MAX_PROJECTION_ITEMS,
                ProjectionSamplingStrategy.STRATIFIED,
                Duration.ofMinutes(5));
    }

    public DefaultVectorProjectionService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            VectorProjectionJobService jobService,
            Executor executor,
            int maxItems,
            int defaultSampleSize) {
        this(projectionRepository, pointRepository, itemRepository, jobService, executor, maxItems, defaultSampleSize,
                ProjectionSamplingStrategy.STRATIFIED, Duration.ofMinutes(5));
    }

    public DefaultVectorProjectionService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            VectorProjectionJobService jobService,
            Executor executor,
            int maxItems,
            int defaultSampleSize,
            Duration processingTimeout) {
        this(projectionRepository, pointRepository, itemRepository, jobService, executor, maxItems, defaultSampleSize,
                ProjectionSamplingStrategy.STRATIFIED, processingTimeout);
    }

    public DefaultVectorProjectionService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            VectorProjectionJobService jobService,
            Executor executor,
            int maxItems,
            int defaultSampleSize,
            ProjectionSamplingStrategy defaultSamplingStrategy,
            Duration processingTimeout) {
        this.projectionRepository = Objects.requireNonNull(projectionRepository, "projectionRepository");
        this.pointRepository = Objects.requireNonNull(pointRepository, "pointRepository");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository");
        this.jobService = Objects.requireNonNull(jobService, "jobService");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.maxItems = Math.max(1, maxItems);
        this.defaultSampleSize = Math.min(this.maxItems, Math.max(1, defaultSampleSize));
        this.defaultSamplingStrategy = defaultSamplingStrategy == null
                ? ProjectionSamplingStrategy.STRATIFIED
                : defaultSamplingStrategy;
        this.processingTimeout = processingTimeout == null || processingTimeout.isZero() || processingTimeout.isNegative()
                ? Duration.ofMinutes(5)
                : processingTimeout;
    }

    @Override
    public VectorProjection create(VectorProjectionCreateCommand command) {
        failStaleProcessingProjections();
        String name = normalize(command.name());
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        VectorProjectionScope scope = normalizeScope(command);
        ProjectionMode mode = command.mode() == null ? ProjectionMode.DETAIL : command.mode();
        if (mode == ProjectionMode.DETAIL && scope.isEmpty()) {
            throw projectionError(HttpStatus.BAD_REQUEST, "PROJECTION_SCOPE_REQUIRED",
                    "Projection scope is required. Specify targetTypes or filters.",
                    null, null);
        }
        validateSampleSize(command.sampleSize());
        long totalCount = itemRepository.count(scope);
        int projectedCount = projectedCount(mode, totalCount, command.sampleSize());
        boolean sampled = totalCount > projectedCount;
        Integer sampleSize = sampled || command.sampleSize() != null ? projectedCount : null;
        ProjectionSamplingStrategy samplingStrategy = command.samplingStrategy() == null
                ? defaultSamplingStrategy
                : command.samplingStrategy();
        VectorProjection projection = VectorProjection.requested(
                newProjectionId(),
                name,
                command.algorithm() == null ? ProjectionAlgorithm.PCA : command.algorithm(),
                scope,
                mode,
                totalCount,
                projectedCount,
                sampled,
                sampleSize,
                samplingStrategy,
                maxItems,
                normalize(command.createdBy()),
                Instant.now());
        projectionRepository.save(projection);
        try {
            executor.execute(() -> jobService.run(projection.projectionId()));
        } catch (RejectedExecutionException ex) {
            projectionRepository.updateStatus(
                    projection.projectionId(),
                    ProjectionStatus.FAILED,
                    "PROJECTION_JOB_QUEUE_UNAVAILABLE",
                    "Projection job could not be queued",
                    Instant.now());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PROJECTION_JOB_QUEUE_UNAVAILABLE", ex);
        }
        return projection;
    }

    @Override
    public VectorProjectionEstimate estimate(VectorProjectionCreateCommand command) {
        VectorProjectionScope scope = normalizeScope(command);
        validateSampleSize(command.sampleSize());
        long totalCount = itemRepository.count(scope);
        return new VectorProjectionEstimate(
                totalCount,
                maxItems,
                totalCount > maxItems,
                (int) Math.min(totalCount, defaultSampleSize),
                defaultSamplingStrategy);
    }

    private VectorProjectionScope normalizeScope(VectorProjectionCreateCommand command) {
        List<String> targetTypes = command.targetTypes().stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (String.join(",", targetTypes).length() > MAX_TARGET_TYPES_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "targetTypes must fit within " + MAX_TARGET_TYPES_LENGTH + " characters");
        }
        java.util.Map<String, Object> filters = new java.util.LinkedHashMap<>();
        command.filters().forEach((key, value) -> {
            String normalizedKey = normalize(key);
            if (normalizedKey != null && value != null) {
                if (!FILTER_KEY_PATTERN.matcher(normalizedKey).matches()) {
                    throw projectionError(HttpStatus.BAD_REQUEST, "PROJECTION_FILTER_INVALID",
                            "Projection filter keys may contain only letters, digits, dot, underscore, and hyphen.",
                            null, null);
                }
                filters.put(normalizedKey, value);
            }
        });
        validateChunkRange(filters);
        return new VectorProjectionScope(targetTypes, filters);
    }

    private void validateChunkRange(java.util.Map<String, Object> filters) {
        Integer from = integerFilter(filters.get("chunkIndexFrom"), "chunkIndexFrom");
        Integer to = integerFilter(filters.get("chunkIndexTo"), "chunkIndexTo");
        if ((from != null && from < 0) || (to != null && to < 0) || (from != null && to != null && from > to)) {
            throw projectionError(HttpStatus.BAD_REQUEST, "PROJECTION_CHUNK_RANGE_INVALID",
                    "chunkIndexFrom and chunkIndexTo must define a non-negative ascending range.",
                    null, null);
        }
        if (from != null) {
            filters.put("chunkIndexFrom", from);
        }
        if (to != null) {
            filters.put("chunkIndexTo", to);
        }
    }

    private Integer integerFilter(Object value, String name) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long longValue = number.longValue();
            if (number.doubleValue() != longValue
                    || longValue < Integer.MIN_VALUE
                    || longValue > Integer.MAX_VALUE) {
                throw projectionError(HttpStatus.BAD_REQUEST, "PROJECTION_CHUNK_RANGE_INVALID",
                        name + " must be an integer.", null, null);
            }
            return (int) longValue;
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw projectionError(HttpStatus.BAD_REQUEST, "PROJECTION_CHUNK_RANGE_INVALID",
                    name + " must be an integer.", null, null);
        }
    }

    private void validateSampleSize(Integer sampleSize) {
        if (sampleSize == null) {
            return;
        }
        if (sampleSize <= 0 || sampleSize > maxItems) {
            throw projectionError(HttpStatus.BAD_REQUEST, "PROJECTION_SAMPLE_SIZE_EXCEEDED",
                    "sampleSize must be between 1 and server maxAllowed.",
                    null, sampleSize);
        }
    }

    private int projectedCount(ProjectionMode mode, long totalCount, Integer requestedSampleSize) {
        if (requestedSampleSize != null) {
            return (int) Math.min(totalCount, requestedSampleSize);
        }
        if (totalCount <= maxItems) {
            return (int) totalCount;
        }
        if (mode == ProjectionMode.DETAIL) {
            throw projectionError(HttpStatus.CONFLICT, "PROJECTION_LIMIT_EXCEEDED",
                    "Projection target count exceeds server limit.",
                    totalCount, null);
        }
        return (int) Math.min(totalCount, defaultSampleSize);
    }

    private VectorProjectionException projectionError(
            HttpStatus status,
            String code,
            String message,
            Long totalCount,
            Integer sampleSize) {
        return new VectorProjectionException(status, code, message, totalCount, maxItems, sampleSize);
    }

    @Override
    public List<VectorProjection> list(int limit, int offset) {
        failStaleProcessingProjections();
        return projectionRepository.findAll(clampLimit(limit), Math.max(0, offset));
    }

    @Override
    public VectorProjection get(String projectionId) {
        failStaleProcessingProjections();
        return projectionRepository.findById(projectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PROJECTION_NOT_FOUND"));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "vectorProjectionPoints", allEntries = true)
    public void delete(String projectionId) {
        VectorProjection projection = get(projectionId);
        if (projection.status() == ProjectionStatus.REQUESTED
                || projection.status() == ProjectionStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PROJECTION_DELETE_IN_PROGRESS");
        }
        pointRepository.deleteByProjectionId(projectionId);
        projectionRepository.deleteById(projectionId);
    }

    @Override
    @Cacheable(cacheNames = "vectorProjectionPoints", key = "{#projectionId, #targetType, #clusterId, #keyword, #limit, #offset}")
    public ProjectionPointPage points(
            String projectionId,
            String targetType,
            String clusterId,
            String keyword,
            int limit,
            int offset) {
        VectorProjection projection = get(projectionId);
        if (projection.status() != ProjectionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PROJECTION_NOT_READY");
        }
        return pointRepository.findPage(
                projectionId,
                normalize(targetType),
                normalize(clusterId),
                normalize(keyword),
                clampLimit(limit),
                Math.max(0, offset));
    }

    @Override
    public VectorItem item(String vectorItemId) {
        return itemRepository.findByVectorItemId(vectorItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VECTOR_ITEM_NOT_FOUND"));
    }

    private void failStaleProcessingProjections() {
        Instant now = Instant.now();
        projectionRepository.markStaleProcessingFailed(
                now.minus(processingTimeout),
                "PROJECTION_JOB_STALE",
                "Projection job exceeded processing timeout without writing points",
                now);
    }

    private int clampLimit(int limit) {
        int effective = limit <= 0 ? 2_000 : limit;
        return Math.min(effective, MAX_LIMIT);
    }

    private String newProjectionId() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
                .withZone(java.time.ZoneOffset.UTC)
                .format(Instant.now());
        return "proj-" + timestamp + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
