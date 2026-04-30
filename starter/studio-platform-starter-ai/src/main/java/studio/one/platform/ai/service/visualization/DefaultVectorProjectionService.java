package studio.one.platform.ai.service.visualization;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;

public class DefaultVectorProjectionService implements VectorProjectionService {

    private static final int MAX_LIMIT = 5_000;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_TARGET_TYPES_LENGTH = 500;

    private final VectorProjectionRepository projectionRepository;
    private final VectorProjectionPointRepository pointRepository;
    private final ExistingVectorItemRepository itemRepository;
    private final VectorProjectionJobService jobService;
    private final Executor executor;

    public DefaultVectorProjectionService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            VectorProjectionJobService jobService,
            Executor executor) {
        this.projectionRepository = Objects.requireNonNull(projectionRepository, "projectionRepository");
        this.pointRepository = Objects.requireNonNull(pointRepository, "pointRepository");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository");
        this.jobService = Objects.requireNonNull(jobService, "jobService");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public VectorProjection create(VectorProjectionCreateCommand command) {
        String name = normalize(command.name());
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        List<String> targetTypes = command.targetTypes().stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .toList();
        if (String.join(",", targetTypes).length() > MAX_TARGET_TYPES_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "targetTypes must fit within " + MAX_TARGET_TYPES_LENGTH + " characters");
        }
        VectorProjection projection = VectorProjection.requested(
                newProjectionId(),
                name,
                command.algorithm(),
                targetTypes,
                command.filters(),
                normalize(command.createdBy()),
                Instant.now());
        projectionRepository.save(projection);
        try {
            executor.execute(() -> jobService.run(projection.projectionId()));
        } catch (RejectedExecutionException ex) {
            projectionRepository.updateStatus(
                    projection.projectionId(),
                    ProjectionStatus.FAILED,
                    "Projection job could not be queued",
                    Instant.now());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PROJECTION_JOB_QUEUE_UNAVAILABLE", ex);
        }
        return projection;
    }

    @Override
    public List<VectorProjection> list(int limit, int offset) {
        return projectionRepository.findAll(clampLimit(limit), Math.max(0, offset));
    }

    @Override
    public VectorProjection get(String projectionId) {
        return projectionRepository.findById(projectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PROJECTION_NOT_FOUND"));
    }

    @Override
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
