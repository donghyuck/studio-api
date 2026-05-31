package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.ai.core.vector.visualization.UmapVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.skillgraph.application.command.GenerateSkillProjectionCommand;
import studio.one.platform.skillgraph.application.result.SkillClusterView;
import studio.one.platform.skillgraph.application.result.SkillGraphBatchJobView;
import studio.one.platform.skillgraph.application.result.SkillProjectionPointView;
import studio.one.platform.skillgraph.application.result.SkillProjectionResult;
import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;
import studio.one.platform.skillgraph.application.usecase.SkillGraphBatchJobNotifier;
import studio.one.platform.skillgraph.application.usecase.SkillVisualizationService;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillProjectionMetadata;
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
        String resolvedProjectionId = normalizeProjectionId(command == null ? null : command.projectionId());
        int max = normalizeLimit(command == null ? 0 : command.limit());
        String reductionAlgorithm = normalizeReductionAlgorithm(command == null ? null : command.reductionAlgorithm());
        String clusteringAlgorithm = normalizeClusteringAlgorithm(command == null ? null : command.clusteringAlgorithm());
        String embeddingProvider = normalizeText(command == null ? null : command.embeddingProvider());
        String embeddingModel = normalizeText(command == null ? null : command.embeddingModel());
        Integer embeddingDimension = command == null ? null : normalizeDimension(command.embeddingDimension());
        Instant now = Instant.now();
        List<SkillVectorItem> skillItems = dictionaryStore.findVectorItems(embeddingProvider, embeddingModel, embeddingDimension, max);
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
                        Map.of("skillId", item.skillId()),
                        item.createdAt()))
                .toList();
        List<SkillProjection> points = project(resolvedProjectionId, vectorItems, reductionAlgorithm, now);
        SkillClusterer.SkillClusteringResult clustered = clusterer.cluster(resolvedProjectionId, points);
        SkillProjectionMetadata metadata = new SkillProjectionMetadata(
                reductionAlgorithm,
                clusteringAlgorithm,
                embeddingProvider,
                embeddingModel,
                embeddingDimension);
        projectionStore.replaceProjection(resolvedProjectionId, clustered.projections(), clustered.clusters(), metadata);
        return new SkillProjectionResult(
                resolvedProjectionId,
                clustered.projections().size(),
                clustered.clusters().size(),
                reductionAlgorithm,
                clusteringAlgorithm,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                clustered.projections().stream().map(SkillProjectionPointView::from).toList(),
                clustered.clusters().stream().map(SkillClusterView::from).toList());
    }

    @Override
    public SkillGraphBatchJobView generateProjectionJob(GenerateSkillProjectionCommand command) {
        GenerateSkillProjectionCommand normalized = normalizeCommand(command);
        String jobId = "skill_cluster_generation_" + UUID.randomUUID();
        int total = dictionaryStore.findVectorItems(
                normalized.embeddingProvider(),
                normalized.embeddingModel(),
                normalized.embeddingDimension(),
                0).size();
        Instant now = Instant.now();
        SkillGraphBatchJob job = new SkillGraphBatchJob(
                jobId,
                SkillGraphBatchJobType.SKILL_CLUSTER_GENERATION,
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
            SkillProjectionResult result = generateProjection(command);
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
                normalizeReductionAlgorithm(command == null ? null : command.reductionAlgorithm()),
                normalizeClusteringAlgorithm(command == null ? null : command.clusteringAlgorithm()),
                normalizeText(command == null ? null : command.embeddingProvider()),
                normalizeText(command == null ? null : command.embeddingModel()),
                command == null ? null : normalizeDimension(command.embeddingDimension()));
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
                {"projectionId":"%s","reductionAlgorithm":"%s","clusteringAlgorithm":"%s","embeddingProvider":"%s","embeddingModel":"%s","embeddingDimension":%d}
                """.formatted(
                command.projectionId(),
                command.reductionAlgorithm(),
                command.clusteringAlgorithm(),
                command.embeddingProvider() == null ? "" : command.embeddingProvider(),
                command.embeddingModel() == null ? "" : command.embeddingModel(),
                command.embeddingDimension() == null ? 0 : command.embeddingDimension()).trim();
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
        return limit;
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

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
