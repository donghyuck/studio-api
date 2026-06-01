package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import studio.one.platform.ai.core.vector.visualization.UmapVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.skillgraph.application.command.GenerateSkillProjectionCommand;
import studio.one.platform.skillgraph.application.result.SkillClusterMemberView;
import studio.one.platform.skillgraph.application.result.SkillClusterView;
import studio.one.platform.skillgraph.application.result.SkillGraphBatchJobView;
import studio.one.platform.skillgraph.application.result.SkillProjectionPointView;
import studio.one.platform.skillgraph.application.result.SkillProjectionResult;
import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;
import studio.one.platform.skillgraph.application.usecase.SkillGraphBatchJobNotifier;
import studio.one.platform.skillgraph.application.usecase.SkillVisualizationService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillClusterMember;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillProjectionMetadata;
import studio.one.platform.skillgraph.domain.model.SkillType;
import studio.one.platform.skillgraph.domain.model.SkillVectorItem;
import studio.one.platform.skillgraph.domain.port.SkillClusterer;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillGraphBatchJobStore;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillGraphBatchJobStore;

/**
 * 스킬 시각화 유스케이스 구현체.
 *
 * 주요 역할:
 * - 스킬 벡터 데이터를 2D 공간에 투영
 * - 투영된 데이터 클러스터링
 * - 시각화 결과 제공
 *
 * 핵심 처리 흐름:
 * 1. Skill Dictionary에서 벡터 아이템 조회
 * 2. UMAP 기반 2D 투영 생성
 * 3. 투영 결과 클러스터링
 * 4. Projection/Cluster 저장 및 반환
 *
 * 현재 구조는 단일 projectionId에 대해 전체 스킬을 투영하는 방식이지만, 향후 사용자별 맞춤 투영, 실시간 업데이트, 다양한 projection 알고리즘 지원 등으로 확장 가능하다.
 *
 * @author donghyuck, son
 * @since 2026-05-17
 *
 *        <pre>
 *
 * &lt;&lt; 개정이력(Modification Information) &gt;&gt;
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2026-05-17  donghyuck, son: 최초 생성.
 *        </pre>
 */
public class DefaultSkillVisualizationService implements SkillVisualizationService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SkillDictionaryStore dictionaryStore;
    private final SkillProjectionStore projectionStore;
    private final SkillClusterer clusterer;
    private final UmapVectorProjectionGenerator projectionGenerator;
    private final Executor projectionJobExecutor;
    private final SkillGraphBatchJobStore jobStore;
    private final SkillGraphBatchJobNotifier jobNotifier;

    public DefaultSkillVisualizationService(
            SkillDictionaryStore dictionaryStore,
            SkillProjectionStore projectionStore,
            SkillClusterer clusterer) {
        this(dictionaryStore, projectionStore, clusterer, new UmapVectorProjectionGenerator());
    }

    public DefaultSkillVisualizationService(
            SkillDictionaryStore dictionaryStore,
            SkillProjectionStore projectionStore,
            SkillClusterer clusterer,
            Executor projectionJobExecutor,
            SkillGraphBatchJobStore jobStore,
            SkillGraphBatchJobNotifier jobNotifier) {
        this(dictionaryStore, projectionStore, clusterer, new UmapVectorProjectionGenerator(), projectionJobExecutor,
                jobStore, jobNotifier);
    }

    public DefaultSkillVisualizationService(
            SkillDictionaryStore dictionaryStore,
            SkillProjectionStore projectionStore,
            SkillClusterer clusterer,
            UmapVectorProjectionGenerator projectionGenerator) {
        this(dictionaryStore, projectionStore, clusterer, projectionGenerator, Runnable::run,
                new InMemorySkillGraphBatchJobStore(), SkillGraphBatchJobNotifier.NOOP);
    }

    public DefaultSkillVisualizationService(
            SkillDictionaryStore dictionaryStore,
            SkillProjectionStore projectionStore,
            SkillClusterer clusterer,
            UmapVectorProjectionGenerator projectionGenerator,
            Executor projectionJobExecutor,
            SkillGraphBatchJobStore jobStore,
            SkillGraphBatchJobNotifier jobNotifier) {
        this.dictionaryStore = dictionaryStore;
        this.projectionStore = projectionStore;
        this.clusterer = clusterer;
        this.projectionGenerator = projectionGenerator == null ? new UmapVectorProjectionGenerator() : projectionGenerator;
        this.projectionJobExecutor = projectionJobExecutor == null ? Runnable::run : projectionJobExecutor;
        this.jobStore = jobStore == null ? new InMemorySkillGraphBatchJobStore() : jobStore;
        this.jobNotifier = jobNotifier == null ? SkillGraphBatchJobNotifier.NOOP : jobNotifier;
    }

    @Override
    public SkillProjectionResult generateProjection(String projectionId, int limit) {
        return generateProjection(new GenerateSkillProjectionCommand(projectionId, limit, "UMAP", "HDBSCAN", null, null, null));
    }

    @Override
    public SkillProjectionResult generateProjection(GenerateSkillProjectionCommand command) {
        return generateProjection(command, null);
    }

    private SkillProjectionResult generateProjection(GenerateSkillProjectionCommand command, String jobId) {
        String resolvedProjectionId = normalizeProjectionId(command == null ? null : command.projectionId());
        int max = normalizeLimit(command == null ? 0 : command.limit());
        String reductionAlgorithm = normalizeReductionAlgorithm(command == null ? null : command.reductionAlgorithm());
        String skillType = normalizeSkillType(command == null ? null : command.skillType());
        String projectionType = normalizeProjectionType(command == null ? null : command.projectionType());
        Integer projectionDimension = normalizeDimension(command == null ? null : command.projectionDimension());
        String clusteringAlgorithm = normalizeClusteringAlgorithm(command == null ? null : command.clusteringAlgorithm());
        String embeddingProvider = normalizeText(command == null ? null : command.embeddingProvider());
        String embeddingModel = normalizeText(command == null ? null : command.embeddingModel());
        Integer embeddingDimension = command == null ? null : normalizeDimension(command.embeddingDimension());
        String parameters = normalizeText(command == null ? null : command.parameters());
        Instant now = Instant.now();
        List<SkillVectorItem> skillItems = dictionaryStore.findVectorItems(skillType, embeddingProvider, embeddingModel,
                embeddingDimension, max);
        List<VectorItem> vectorItems = skillItems.stream()
                .map(item -> new VectorItem(
                        item.skillId(),
                        "skill",
                        item.skillId(),
                        item.label(),
                        item.label(),
                        item.embedding(),
                        item.embeddingModel(),
                        item.embedding().size(),
                        Map.of("skillId", item.skillId(), "skillType", item.skillType()),
                        item.createdAt()))
                .toList();
        List<SkillProjection> points = project(resolvedProjectionId, vectorItems, reductionAlgorithm, now);
        SkillClusterer.SkillClusteringResult clustered = clusterer.cluster(resolvedProjectionId, points);
        ClusterRunArtifacts artifacts = buildClusterRunArtifacts(
                resolvedProjectionId,
                skillType,
                jobId,
                clusteringAlgorithm,
                reductionAlgorithm,
                projectionType,
                projectionDimension,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                parameters,
                clustered.projections(),
                clustered.clusters(),
                skillItems);
        SkillProjectionMetadata metadata = new SkillProjectionMetadata(
                jobId,
                skillType,
                projectionType,
                reductionAlgorithm,
                projectionDimension,
                clusteringAlgorithm,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                parameters);
        projectionStore.replaceProjection(resolvedProjectionId, artifacts.projections(), artifacts.clusters(),
                artifacts.members(), metadata);
        return new SkillProjectionResult(
                resolvedProjectionId,
                clustered.projections().size(),
                clustered.clusters().size(),
                skillType,
                projectionType,
                reductionAlgorithm,
                projectionDimension,
                clusteringAlgorithm,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                parameters,
                artifacts.projections().stream().map(SkillProjectionPointView::from).toList(),
                artifacts.clusters().stream().map(SkillClusterView::from).toList());
    }

    @Override
    public SkillGraphBatchJobView generateProjectionJob(GenerateSkillProjectionCommand command) {
        GenerateSkillProjectionCommand normalized = normalizeCommand(command);
        String jobId = "projection_generation_" + UUID.randomUUID();
        int total = dictionaryStore.findVectorItems(
                normalized.skillType(),
                normalized.embeddingProvider(),
                normalized.embeddingModel(),
                normalized.embeddingDimension(),
                0).size();
        Instant now = Instant.now();
        SkillGraphBatchJob job = new SkillGraphBatchJob(
                jobId,
                SkillGraphBatchJobType.PROJECTION_GENERATION,
                SkillGraphBatchJobStatus.CREATED,
                total,
                total,
                0,
                0,
                0,
                0,
                normalized.embeddingProvider(),
                normalized.embeddingModel(),
                normalized.embeddingDimension() == null ? 0 : normalized.embeddingDimension(),
                requestSnapshot(normalized),
                "Skill cluster generation job is queued",
                null,
                now,
                null,
                now,
                null);
        saveJob(job);
        try {
            projectionJobExecutor.execute(() -> runProjectionJob(jobId, normalized, total));
        } catch (RejectedExecutionException ex) {
            Instant failedAt = Instant.now();
            SkillGraphBatchJob failed = job.withProgress(
                    SkillGraphBatchJobStatus.FAILED,
                    0,
                    0,
                    total,
                    0,
                    "Skill cluster generation queue is full",
                    failedAt,
                    failedAt);
            return SkillGraphBatchJobView.from(saveJob(failed));
        }
        return SkillGraphBatchJobView.from(currentJob(jobId).orElse(job));
    }

    @Override
    public Page<SkillProjectionSummaryView> listProjections(Pageable pageable) {
        return projectionStore.listProjections(pageable)
                .map(SkillProjectionSummaryView::from);
    }

    @Override
    public Page<SkillProjectionPointView> findProjectionPoints(String projectionId, String clusterId,
            Pageable pageable) {
        return projectionStore.findProjectionPoints(projectionId, clusterId, pageable)
                .map(SkillProjectionPointView::from);
    }

    @Override
    public List<SkillClusterView> findClusters(String projectionId) {
        return projectionStore.findClusters(projectionId).stream()
                .map(SkillClusterView::from)
                .toList();
    }

    @Override
    public Page<SkillClusterMemberView> findClusterMembers(String projectionId, String clusterId, Pageable pageable) {
        return projectionStore.findClusterMembers(projectionId, clusterId, pageable)
                .map(SkillClusterMemberView::from);
    }

    private SkillProjection toSkillProjection(VectorProjectionPoint point) {
        return new SkillProjection(
                point.projectionId(),
                point.vectorItemId(),
                point.x(),
                point.y(),
                point.clusterId(),
                point.displayOrder() == null ? 0 : point.displayOrder(),
                point.createdAt());
    }

    private List<SkillProjection> project(String projectionId, List<VectorItem> vectorItems, String reductionAlgorithm, Instant now) {
        if ("PCA".equals(reductionAlgorithm)) {
            return projectWithPca(projectionId, vectorItems, now);
        }
        return projectionGenerator.generate(projectionId, vectorItems, now).stream()
                .map(this::toSkillProjection)
                .toList();
    }

    private void runProjectionJob(String jobId, GenerateSkillProjectionCommand command, int total) {
        Instant startedAt = Instant.now();
        currentJob(jobId)
                .map(job -> job.markStarted(startedAt, "Skill vectors are being projected"))
                .ifPresent(this::saveJob);
        try {
            SkillProjectionResult result = generateProjection(command, jobId);
            Instant completedAt = Instant.now();
            currentJob(jobId)
                    .map(job -> job.withProgress(
                            SkillGraphBatchJobStatus.COMPLETED,
                            result.itemCount(),
                            result.clusterCount(),
                            0,
                            Math.max(0, total - result.itemCount()),
                            "Skill cluster generation completed: " + result.projectionId(),
                            completedAt,
                            completedAt))
                    .ifPresent(this::saveJob);
        } catch (RuntimeException ex) {
            Instant failedAt = Instant.now();
            currentJob(jobId)
                    .map(job -> job.withProgress(
                            SkillGraphBatchJobStatus.FAILED,
                            0,
                            0,
                            total,
                            0,
                            ex.getMessage(),
                            failedAt,
                            failedAt))
                    .ifPresent(this::saveJob);
        }
    }

    private GenerateSkillProjectionCommand normalizeCommand(GenerateSkillProjectionCommand command) {
        String projectionId = normalizeProjectionId(command == null ? null : command.projectionId());
        return new GenerateSkillProjectionCommand(
                projectionId,
                normalizeLimit(command == null ? 0 : command.limit()),
                normalizeSkillType(command == null ? null : command.skillType()),
                normalizeProjectionType(command == null ? null : command.projectionType()),
                normalizeReductionAlgorithm(command == null ? null : command.reductionAlgorithm()),
                normalizeDimension(command == null ? null : command.projectionDimension()),
                normalizeClusteringAlgorithm(command == null ? null : command.clusteringAlgorithm()),
                normalizeText(command == null ? null : command.embeddingProvider()),
                normalizeText(command == null ? null : command.embeddingModel()),
                command == null ? null : normalizeDimension(command.embeddingDimension()),
                normalizeText(command == null ? null : command.parameters()));
    }

    private Optional<SkillGraphBatchJob> currentJob(String jobId) {
        return jobStore.findById(jobId);
    }

    private SkillGraphBatchJob saveJob(SkillGraphBatchJob job) {
        SkillGraphBatchJob saved = jobStore.save(job);
        jobNotifier.notifyJob(saved);
        return saved;
    }

    private String requestSnapshot(GenerateSkillProjectionCommand command) {
        return """
                {"projectionId":"%s","skillType":"%s","projectionType":"%s","reductionAlgorithm":"%s","projectionDimension":%d,"clusteringAlgorithm":"%s","embeddingProvider":"%s","embeddingModel":"%s","embeddingDimension":%d}
                """.formatted(
                command.projectionId(),
                command.skillType(),
                command.projectionType(),
                command.reductionAlgorithm(),
                command.projectionDimension() == null ? 0 : command.projectionDimension(),
                command.clusteringAlgorithm(),
                command.embeddingProvider() == null ? "" : command.embeddingProvider(),
                command.embeddingModel() == null ? "" : command.embeddingModel(),
                command.embeddingDimension() == null ? 0 : command.embeddingDimension()).trim();
    }

    private ClusterRunArtifacts buildClusterRunArtifacts(
            String projectionId,
            String skillType,
            String jobId,
            String clusteringAlgorithm,
            String reductionAlgorithm,
            String projectionType,
            Integer projectionDimension,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension,
            String parameters,
            List<SkillProjection> projections,
            List<SkillCluster> clusters,
            List<SkillVectorItem> skillItems) {
        Map<String, SkillVectorItem> itemById = new HashMap<>();
        for (SkillVectorItem item : skillItems) {
            itemById.put(item.skillId(), item);
        }
        Map<String, String> scopedClusterIdByRaw = new HashMap<>();
        for (SkillCluster cluster : clusters) {
            scopedClusterIdByRaw.put(cluster.clusterId(), scopedClusterId(projectionId, cluster.clusterId()));
        }
        List<SkillProjection> scopedProjections = projections.stream()
                .map(projection -> {
                    String rawClusterId = projection.clusterId();
                    if (rawClusterId == null || rawClusterId.isBlank()) {
                        return projection;
                    }
                    String scoped = scopedClusterIdByRaw.getOrDefault(rawClusterId, scopedClusterId(projectionId, rawClusterId));
                    return projection.withClusterId(scoped);
                })
                .toList();
        Map<String, List<SkillProjection>> byCluster = new HashMap<>();
        for (SkillProjection projection : scopedProjections) {
            if (projection.clusterId() != null && !projection.clusterId().isBlank()) {
                byCluster.computeIfAbsent(projection.clusterId(), ignored -> new ArrayList<>()).add(projection);
            }
        }
        List<SkillClusterMember> members = new ArrayList<>();
        List<SkillCluster> enriched = new ArrayList<>();
        for (SkillCluster cluster : clusters) {
            String scopedClusterId = scopedClusterIdByRaw.getOrDefault(cluster.clusterId(), cluster.clusterId());
            List<SkillProjection> clusterPoints = byCluster.getOrDefault(scopedClusterId, List.of());
            Centroid centroid = centroid(clusterPoints);
            List<SkillProjectionDistance> ranked = clusterPoints.stream()
                    .map(point -> new SkillProjectionDistance(point, distance(point, centroid)))
                    .sorted(Comparator.comparingDouble(SkillProjectionDistance::distance)
                            .thenComparing(item -> itemById.getOrDefault(item.projection().skillId(),
                                    new SkillVectorItem(item.projection().skillId(), item.projection().skillId(),
                                            List.of(), null, null)).label()))
                    .toList();
            List<String> representatives = ranked.stream()
                    .limit(5)
                    .map(item -> item.projection().skillId())
                    .toList();
            String centroidProjectionId = representatives.isEmpty() ? null : representatives.get(0);
            for (SkillProjectionDistance item : ranked) {
                members.add(new SkillClusterMember(
                        scopedClusterId,
                        item.projection().skillId(),
                        null,
                        projectionId,
                        1.0d,
                        item.distance(),
                        representatives.contains(item.projection().skillId())));
            }
            enriched.add(new SkillCluster(
                    scopedClusterId,
                    cluster.label(),
                    cluster.algorithm(),
                    cluster.itemCount(),
                    skillType,
                    jobId,
                    parseClusterLabel(cluster.clusterId()),
                    representatives,
                    centroidProjectionId,
                    confidence(clusterPoints.size(), scopedProjections.size()),
                    clusterMetadata(skillType, embeddingProvider, embeddingModel, embeddingDimension, reductionAlgorithm,
                            projectionType, projectionDimension, clusteringAlgorithm, parameters),
                    cluster.createdAt()));
        }
        return new ClusterRunArtifacts(enriched, members, scopedProjections);
    }

    private Centroid centroid(List<SkillProjection> projections) {
        if (projections == null || projections.isEmpty()) {
            return new Centroid(0.0d, 0.0d);
        }
        double x = 0.0d;
        double y = 0.0d;
        for (SkillProjection projection : projections) {
            x += projection.x();
            y += projection.y();
        }
        return new Centroid(x / projections.size(), y / projections.size());
    }

    private double distance(SkillProjection projection, Centroid centroid) {
        double x = projection.x() - centroid.x();
        double y = projection.y() - centroid.y();
        return Math.sqrt((x * x) + (y * y));
    }

    private Double confidence(int memberCount, int totalCount) {
        if (memberCount <= 0 || totalCount <= 0) {
            return 0.0d;
        }
        return Math.min(1.0d, memberCount / (double) totalCount);
    }

    private Integer parseClusterLabel(String clusterId) {
        String normalized = normalizeText(clusterId);
        if (normalized == null) {
            return null;
        }
        int index = normalized.lastIndexOf('-');
        String value = index < 0 ? normalized : normalized.substring(index + 1);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String clusterMetadata(
            String skillType,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension,
            String reductionAlgorithm,
            String projectionType,
            Integer projectionDimension,
            String clusteringAlgorithm,
            String parameters) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("skillType", skillType);
        payload.put("embeddingProvider", embeddingProvider);
        payload.put("embeddingModel", embeddingModel);
        payload.put("embeddingDimension", embeddingDimension);
        payload.put("projectionAlgorithm", reductionAlgorithm);
        payload.put("projectionType", projectionType);
        payload.put("projectionDimension", projectionDimension);
        payload.put("clusteringAlgorithm", clusteringAlgorithm);
        payload.put("parameters", parameters);
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String scopedClusterId(String projectionId, String clusterId) {
        String seed = projectionId + "::" + clusterId;
        return "cl_" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private List<SkillProjection> projectWithPca(String projectionId, List<VectorItem> vectorItems, Instant now) {
        if (vectorItems == null || vectorItems.isEmpty()) {
            return List.of();
        }
        int dimension = vectorItems.stream()
                .map(VectorItem::embedding)
                .filter(values -> values != null && !values.isEmpty())
                .mapToInt(List::size)
                .min()
                .orElse(0);
        if (dimension == 0) {
            return List.of();
        }
        double[] mean = new double[dimension];
        for (VectorItem item : vectorItems) {
            List<Double> embedding = item.embedding();
            for (int i = 0; i < dimension; i++) {
                mean[i] += embedding.get(i);
            }
        }
        for (int i = 0; i < dimension; i++) {
            mean[i] /= vectorItems.size();
        }
        List<double[]> centered = new ArrayList<>(vectorItems.size());
        for (VectorItem item : vectorItems) {
            double[] values = new double[dimension];
            List<Double> embedding = item.embedding();
            for (int i = 0; i < dimension; i++) {
                values[i] = embedding.get(i) - mean[i];
            }
            centered.add(values);
        }
        double[] first = principalComponent(centered, dimension, null);
        double[] second = principalComponent(centered, dimension, first);
        List<SkillProjection> projections = new ArrayList<>(vectorItems.size());
        for (int index = 0; index < vectorItems.size(); index++) {
            VectorItem item = vectorItems.get(index);
            double[] values = centered.get(index);
            projections.add(new SkillProjection(
                    projectionId,
                    item.vectorItemId(),
                    dot(values, first),
                    dot(values, second),
                    null,
                    index,
                    now));
        }
        return projections;
    }

    private double[] principalComponent(List<double[]> vectors, int dimension, double[] orthogonalTo) {
        double[] component = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            component[i] = 1.0d / Math.sqrt(dimension);
        }
        for (int iteration = 0; iteration < 40; iteration++) {
            double[] next = new double[dimension];
            for (double[] vector : vectors) {
                double projection = dot(vector, component);
                for (int i = 0; i < dimension; i++) {
                    next[i] += projection * vector[i];
                }
            }
            if (orthogonalTo != null) {
                double overlap = dot(next, orthogonalTo);
                for (int i = 0; i < dimension; i++) {
                    next[i] -= overlap * orthogonalTo[i];
                }
            }
            normalize(next);
            component = next;
        }
        return component;
    }

    private double dot(double[] left, double[] right) {
        double value = 0.0d;
        int size = Math.min(left.length, right.length);
        for (int i = 0; i < size; i++) {
            value += left[i] * right[i];
        }
        return value;
    }

    private void normalize(double[] values) {
        double norm = 0.0d;
        for (double value : values) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0d) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] /= norm;
        }
    }

    private String normalizeProjectionId(String projectionId) {
        if (projectionId == null || projectionId.isBlank()) {
            return "skp_" + UUID.randomUUID();
        }
        return projectionId.trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return Math.min(limit, SkillGraphLimits.MAX_PROJECTION_ITEMS);
    }

    private String normalizeReductionAlgorithm(String algorithm) {
        String normalized = normalizeText(algorithm);
        if (normalized == null || "UMA".equalsIgnoreCase(normalized) || "UMAP".equalsIgnoreCase(normalized)) {
            return "UMAP";
        }
        if ("PCA".equalsIgnoreCase(normalized)) {
            return "PCA";
        }
        throw new IllegalArgumentException("Unsupported reduction algorithm: " + algorithm);
    }

    private String normalizeClusteringAlgorithm(String algorithm) {
        String normalized = normalizeText(algorithm);
        if (normalized == null || "HDBSCAN".equalsIgnoreCase(normalized)) {
            return "HDBSCAN";
        }
        return normalized.toUpperCase();
    }

    private Integer normalizeDimension(Integer dimension) {
        return dimension == null || dimension <= 0 ? null : dimension;
    }

    private String normalizeSkillType(String value) {
        return SkillType.normalizeNameOrNull(value);
    }

    private String normalizeProjectionType(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? "VISUALIZATION" : normalized.toUpperCase();
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ClusterRunArtifacts(
            List<SkillCluster> clusters,
            List<SkillClusterMember> members,
            List<SkillProjection> projections) {
    }

    private record Centroid(double x, double y) {
    }

    private record SkillProjectionDistance(SkillProjection projection, double distance) {
    }
}
