package studio.one.platform.markdown.autoconfigure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.service.pipeline.RagChunkStage;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.artifact.ChunkSet;
import studio.one.platform.chunking.artifact.ChunkSetItem;
import studio.one.platform.chunking.artifact.ChunkSetQualityStatus;
import studio.one.platform.chunking.artifact.ChunkSetStatus;
import studio.one.platform.chunking.artifact.ChunkSetStore;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.markdown.application.MarkdownIdeaBlockSummary;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeApplyOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeApplyResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergePreview;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergePreviewOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeUndoOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeUndoResult;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.MarkdownPipelineProgress;
import studio.one.platform.markdown.application.port.MarkdownPipelinePort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;

public class MarkdownDownstreamPipelineAdapter implements MarkdownPipelinePort {
    private static final Logger log = LoggerFactory.getLogger(MarkdownDownstreamPipelineAdapter.class);
    private static final String BLOCKIFY_SCHEMA_VERSION = "blockify-metadata-v1";
    private static final String BLOCKIFY_VALIDATION_FALLBACK = "FALLBACK";
    private static final String BLOCKIFY_UNKNOWN_FALLBACK = "UNKNOWN_FALLBACK";
    private static final String REQUIRE_RAG_CHUNK_STAGE = "requireRagChunkStage";
    private static final double DEFAULT_EMBEDDING_SIMILARITY_THRESHOLD = 0.90d;
    private static final Pattern FACT_TOKEN = Pattern.compile(
            "\\b\\d+(?:[.,]\\d+)?\\s*(?:일|개월|년|시간|분|회|명|원|만원|%|퍼센트|점|세|주)\\b");

    private final ObjectProvider<RagIndexJobService> ragJobServiceProvider;
    private final ObjectProvider<SkillRagExtractionJobService> skillJobServiceProvider;
    private final ObjectProvider<ChunkingOrchestrator> chunkingProvider;
    private final ObjectProvider<RagChunkStageStore> chunkStageStoreProvider;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;
    private final ObjectProvider<AiProviderRegistry> aiProviderRegistryProvider;
    private final ObjectProvider<ChunkSetStore> chunkSetStoreProvider;
    private final MarkdownRepository repository;
    private final ObjectMapper objectMapper;

    public MarkdownDownstreamPipelineAdapter(ObjectProvider<RagIndexJobService> ragJobServiceProvider,
            ObjectProvider<SkillRagExtractionJobService> skillJobServiceProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            MarkdownRepository repository) {
        this(ragJobServiceProvider, skillJobServiceProvider, chunkingProvider, chunkStageStoreProvider, null,
                null, repository);
    }

    public MarkdownDownstreamPipelineAdapter(ObjectProvider<RagIndexJobService> ragJobServiceProvider,
            ObjectProvider<SkillRagExtractionJobService> skillJobServiceProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            MarkdownRepository repository) {
        this(ragJobServiceProvider, skillJobServiceProvider, chunkingProvider, chunkStageStoreProvider,
                embeddingPortProvider, null, repository);
    }

    public MarkdownDownstreamPipelineAdapter(ObjectProvider<RagIndexJobService> ragJobServiceProvider,
            ObjectProvider<SkillRagExtractionJobService> skillJobServiceProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<AiProviderRegistry> aiProviderRegistryProvider,
            MarkdownRepository repository) {
        this(ragJobServiceProvider, skillJobServiceProvider, chunkingProvider, chunkStageStoreProvider,
                embeddingPortProvider, aiProviderRegistryProvider, null, repository, new ObjectMapper());
    }

    public MarkdownDownstreamPipelineAdapter(ObjectProvider<RagIndexJobService> ragJobServiceProvider,
            ObjectProvider<SkillRagExtractionJobService> skillJobServiceProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<AiProviderRegistry> aiProviderRegistryProvider,
            MarkdownRepository repository,
            ObjectMapper objectMapper) {
        this(ragJobServiceProvider, skillJobServiceProvider, chunkingProvider, chunkStageStoreProvider,
                embeddingPortProvider, aiProviderRegistryProvider, null, repository, objectMapper);
    }

    public MarkdownDownstreamPipelineAdapter(ObjectProvider<RagIndexJobService> ragJobServiceProvider,
            ObjectProvider<SkillRagExtractionJobService> skillJobServiceProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<AiProviderRegistry> aiProviderRegistryProvider,
            ObjectProvider<ChunkSetStore> chunkSetStoreProvider,
            MarkdownRepository repository,
            ObjectMapper objectMapper) {
        this.ragJobServiceProvider = ragJobServiceProvider;
        this.skillJobServiceProvider = skillJobServiceProvider;
        this.chunkingProvider = chunkingProvider;
        this.chunkStageStoreProvider = chunkStageStoreProvider;
        this.embeddingPortProvider = embeddingPortProvider;
        this.aiProviderRegistryProvider = aiProviderRegistryProvider;
        this.chunkSetStoreProvider = chunkSetStoreProvider;
        this.repository = repository;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public void process(MarkdownRevision revision, boolean runChunking, boolean runRagIndex,
            boolean runSkillExtraction) {
        process(revision, new MarkdownPipelineOptions(runChunking, runRagIndex, runSkillExtraction));
    }

    @Override
    public void process(MarkdownRevision revision, MarkdownPipelineOptions options) {
        process(revision, options, MarkdownPipelineStage.CHUNKING, stage -> {
        });
    }

    @Override
    public void process(MarkdownRevision revision, MarkdownPipelineOptions options,
            MarkdownPipelineStage fromStage, Consumer<MarkdownPipelineStage> stageCompleted) {
        String objectType = "attachment";
        String objectId = Long.toString(revision.sourceAttachmentId());
        Map<String, Object> metadata = metadata(revision, objectType, objectId);
        addPipelineMetadata(metadata, options);
        ChunkSet preparedChunkSet = null;
        if (options.runChunking() && fromStage.ordinal() <= MarkdownPipelineStage.CHUNKING.ordinal()) {
            preparedChunkSet = stageChunks(revision, objectType, objectId, metadata, options);
            stageCompleted.accept(MarkdownPipelineStage.CHUNKING);
        }
        if (options.runRagIndex() && fromStage.ordinal() <= MarkdownPipelineStage.RAG_INDEX.ordinal()) {
            assertRagIndexEligible(revision);
            if (preparedChunkSet == null) {
                preparedChunkSet = resolvePreparedChunkSet(revision, objectType, objectId, metadata, options);
            }
            if (!preparedChunkSet.indexEligible()) {
                throw new IllegalStateException("Prepared ChunkSet is not eligible for RAG indexing: "
                        + preparedChunkSet.chunkSetId());
            }
            RagIndexJobService ragJobService = ragJobServiceProvider.getIfAvailable();
            if (ragJobService == null) {
                throw new IllegalStateException("RAG index job service is not configured");
            }
            Map<String, Object> ragMetadata = new HashMap<>(metadata);
            ragMetadata.put("chunkSetId", preparedChunkSet.chunkSetId());
            ragMetadata.put("requirePreparedChunks", true);
            ragMetadata.put("ragRechunkApplied", false);
            if (isIdeaBlockStrategy(options)) {
                ragMetadata.put(REQUIRE_RAG_CHUNK_STAGE, true);
            }
            RagIndexJob job = ragJobService.createJob(new RagIndexJobCreateRequest(
                    objectType, objectId, revision.documentId(), objectType,
                    true, null, revision.sourceFileName()),
                    new RagIndexJobSourceRequest(
                            ragMetadata, List.of(), options.useLlmKeywordExtraction(),
                            options.embeddingProfileId(), options.embeddingProvider(), options.embeddingModel(),
                            preparedChunkSet.chunkSetId(), true));
            RagIndexJob completed = ragJobService.startJob(job.jobId());
            if (completed.status() != RagIndexJobStatus.SUCCEEDED
                    && completed.status() != RagIndexJobStatus.WARNING) {
                throw new IllegalStateException(completed.errorMessage() == null
                        ? "Markdown RAG index job failed: " + completed.jobId()
                        : completed.errorMessage());
            }
            stageCompleted.accept(MarkdownPipelineStage.RAG_INDEX);
        }
        if (options.runSkillExtraction()
                && fromStage.ordinal() <= MarkdownPipelineStage.SKILL_EXTRACTION.ordinal()) {
            SkillRagExtractionJobService skillService = skillJobServiceProvider.getIfAvailable();
            if (skillService == null) {
                throw new IllegalStateException("Skill extraction service is not configured");
            }
            skillService.submitAllChunks(
                    objectType,
                    objectId,
                    null,
                    false,
                    options.generateSkillEmbeddings(),
                    options.skillEmbeddingProvider(),
                    options.skillEmbeddingModel(),
                    options.skillEmbeddingDimension(),
                    options.skillExtractionMode());
            stageCompleted.accept(MarkdownPipelineStage.SKILL_EXTRACTION);
        }
    }

    @Override
    public int estimateChunkCount(MarkdownRevision revision, MarkdownPipelineOptions options) {
        ChunkingOrchestrator chunking = chunkingProvider.getIfAvailable();
        if (chunking == null || revision == null || revision.markdownText() == null || revision.markdownText().isBlank()) {
            return MarkdownPipelinePort.super.estimateChunkCount(revision, options);
        }
        if (options != null && isIdeaBlockStrategy(options.chunkingStrategy())) {
            return MarkdownPipelinePort.super.estimateChunkCount(revision, options);
        }
        String objectType = "attachment";
        String objectId = Long.toString(revision.sourceAttachmentId());
        Map<String, Object> metadata = metadata(revision, objectType, objectId);
        addPipelineMetadata(metadata, options);
        NormalizedDocument document = normalizedDocument(revision, metadata, options);
        return chunking.chunk(document, chunkingContext(document, options).build()).size();
    }

    @Override
    public MarkdownPipelineProgress.RagProgress latestRagProgress(MarkdownRevision revision) {
        RagIndexJobService ragJobService = ragJobServiceProvider.getIfAvailable();
        if (ragJobService == null || revision == null) {
            return null;
        }
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable(RagChunkStageStore::noop);
        if (chunkStages(stageStore, revision).isEmpty()) {
            return null;
        }
        RagIndexJobFilter filter = new RagIndexJobFilter(
                null,
                "attachment",
                Long.toString(revision.sourceAttachmentId()),
                revision.documentId());
        var page = ragJobService.listJobs(filter, new RagIndexJobPageRequest(0, 1),
                new RagIndexJobSort(RagIndexJobSort.Field.CREATED_AT, RagIndexJobSort.Direction.DESC));
        if (page == null || page.jobs().isEmpty()) {
            return null;
        }
        RagIndexJob job = page.jobs().get(0);
        return new MarkdownPipelineProgress.RagProgress(
                job.jobId(),
                job.status() == null ? null : job.status().name(),
                job.currentStep() == null ? null : job.currentStep().name(),
                job.chunkCount(),
                job.embeddedCount(),
                job.indexedCount(),
                job.warningCount(),
                job.errorMessage());
    }

    @Override
    public MarkdownPipelineProgress.ChunkingProgress latestChunkingProgress(MarkdownRevision revision) {
        if (revision == null) {
            return null;
        }
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable(RagChunkStageStore::noop);
        List<RagChunkStage> stages = chunkStages(stageStore, revision);
        if (stages.isEmpty()) {
            return null;
        }
        int ideaBlockCount = 0;
        int fallbackCount = 0;
        Map<String, Integer> fallbackReasonCounts = new LinkedHashMap<>();
        Set<Integer> sourceBlockTargets = new LinkedHashSet<>();
        Set<Integer> sourceBlockCovered = new LinkedHashSet<>();
        int reportedTargetCount = 0;
        int reportedCoveredCount = 0;
        double confidenceSum = 0.0d;
        int confidenceCount = 0;
        for (RagChunkStage stage : stages) {
            Map<String, Object> metadata = stage.metadata();
            boolean ideaBlock = "ideaBlock".equals(text(metadata.get("chunkType")))
                    || ChunkingStrategyType.BLOCKIFY.value().equals(text(metadata.get("actualChunkingStrategy")));
            boolean fallback = BLOCKIFY_VALIDATION_FALLBACK.equals(text(metadata.get("validationStatus")))
                    || ChunkingStrategyType.STRUCTURE_BASED.value().equals(text(metadata.get("actualChunkingStrategy")));
            if (ideaBlock) {
                ideaBlockCount++;
            }
            if (fallback) {
                fallbackCount++;
                String reason = text(metadata.get("fallbackReason"));
                fallbackReasonCounts.merge(reason == null ? BLOCKIFY_UNKNOWN_FALLBACK : reason, 1, Integer::sum);
            }
            addRange(sourceBlockTargets, metadata.get("sourceBlockRange"));
            if (ideaBlock || fallback) {
                addRange(sourceBlockCovered, metadata.get("sourceBlockRange"));
            }
            reportedTargetCount = Math.max(reportedTargetCount,
                    integer(metadata.get("ideaBlockSourceBlockTargetCount"), 0));
            reportedCoveredCount = Math.max(reportedCoveredCount,
                    integer(metadata.get("ideaBlockSourceBlockCoveredCount"), 0));
            Double confidence = doubleValue(metadata.get("confidence"));
            if (confidence != null) {
                confidenceSum += confidence;
                confidenceCount++;
            }
        }
        int targetCount = Math.max(reportedTargetCount, sourceBlockTargets.size());
        int coveredCount = Math.max(reportedCoveredCount, sourceBlockCovered.size());
        double coverage = targetCount == 0 ? 0.0d : coveredCount / (double) targetCount;
        Double averageConfidence = confidenceCount == 0 ? null : confidenceSum / confidenceCount;
        String qualityStatus = chunkingQualityStatus(stages.size(), ideaBlockCount, fallbackCount);
        return new MarkdownPipelineProgress.ChunkingProgress(stages.size(), ideaBlockCount, fallbackCount,
                qualityStatus, Map.copyOf(fallbackReasonCounts), targetCount, coveredCount, coverage, averageConfidence);
    }

    @Override
    public MarkdownIdeaBlockSummary ideaBlockSummary(MarkdownRevision revision) {
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable();
        if (stageStore == null || revision == null) {
            return null;
        }
        List<RagChunkStage> stages = chunkStages(stageStore, revision);
        if (stages.isEmpty()) {
            return new MarkdownIdeaBlockSummary(revision.documentId(), revision.revisionId(), 0.0d,
                    0, 0, 0, "NO_CHUNKS", 0, 0, 0, 0, DEFAULT_EMBEDDING_SIMILARITY_THRESHOLD,
                    Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        MarkdownPipelineProgress.ChunkingProgress progress = latestChunkingProgress(revision);
        List<String> rejectedReasons = progress == null ? List.of()
                : progress.fallbackReasonCounts().keySet().stream().sorted().toList();
        List<MarkdownIdeaBlockSummary.MergeCandidateCluster> mergeCandidateClusters =
                mergeCandidateClusters(stages);
        int mergeCandidateCount = mergeCandidateClusters.stream()
                .mapToInt(MarkdownIdeaBlockSummary.MergeCandidateCluster::size)
                .sum();
        List<MarkdownIdeaBlockSummary.MergeCandidateCluster> embeddingCandidateClusters =
                embeddingCandidateClusters(stages);
        Map<String, MarkdownIdeaBlockSummary.MergeCandidateCluster> embeddingClusterByChunkId =
                clusterByChunkId(embeddingCandidateClusters);
        int embeddingMergeCandidateCount = embeddingCandidateClusters.stream()
                .mapToInt(MarkdownIdeaBlockSummary.MergeCandidateCluster::size)
                .sum();
        int summaryIdeaBlockCount = progress == null ? countIdeaBlocks(stages) : progress.ideaBlockCount();
        Map<String, Integer> typedFieldCounts = typedFieldCounts(stages);
        List<MarkdownIdeaBlockSummary.SampleIdeaBlock> samples = stages.stream()
                .filter(stage -> ChunkingStrategyType.BLOCKIFY.value()
                        .equals(text(stage.metadata().get("actualChunkingStrategy")))
                        || "ideaBlock".equals(text(stage.metadata().get("chunkType"))))
                .limit(5)
                .map(stage -> sampleIdeaBlock(stage, embeddingClusterByChunkId.get(stage.chunkId())))
                .toList();
        List<Integer> missingSourceBlocks = missingSourceBlocks(stages);
        return new MarkdownIdeaBlockSummary(
                revision.documentId(),
                revision.revisionId(),
                progress == null ? 0.0d : progress.sourceBlockCoverage(),
                progress == null ? stages.size() : progress.chunkCount(),
                summaryIdeaBlockCount,
                progress == null ? 0 : progress.fallbackCount(),
                progress == null ? "UNKNOWN" : progress.qualityStatus(),
                firstMetadataValue(stages, "requestedDocumentType"),
                firstMetadataValue(stages, "detectedDocumentType"),
                firstDoubleMetadataValue(stages, "documentTypeConfidence"),
                firstMetadataValue(stages, "documentTypeReason"),
                firstMetadataValue(stages, "blockifyProfile"),
                firstMetadataValue(stages, "ideaBlockSchemaVersion"),
                typedFieldCounts,
                typedFieldCoverage(typedFieldCounts, summaryIdeaBlockCount),
                mergeCandidateCount,
                mergeCandidateClusters.size(),
                embeddingMergeCandidateCount,
                embeddingCandidateClusters.size(),
                DEFAULT_EMBEDDING_SIMILARITY_THRESHOLD,
                progress == null ? Map.of() : progress.fallbackReasonCounts(),
                missingSourceBlocks,
                rejectedReasons,
                mergeCandidateClusters,
                embeddingCandidateClusters,
                samples);
    }

    private List<RagChunkStage> chunkStages(RagChunkStageStore stageStore, MarkdownRevision revision) {
        List<RagChunkStage> stages = stageStore.findByObject(
                "attachment", Long.toString(revision.sourceAttachmentId()), revision.documentId());
        if (!stages.isEmpty()) {
            return stages;
        }
        stages = stageStore.findIndexedByObject(
                "attachment", Long.toString(revision.sourceAttachmentId()), revision.revisionId());
        if (!stages.isEmpty()) {
            return stages;
        }
        return chunkSetStore().findLatest(
                        "attachment", Long.toString(revision.sourceAttachmentId()),
                        revision.documentId(), revision.revisionId())
                .map(this::chunkSetStages)
                .orElse(List.of());
    }

    private List<RagChunkStage> chunkSetStages(ChunkSet chunkSet) {
        return chunkSet.items().stream()
                .map(item -> new RagChunkStage(
                        chunkSet.objectType(), chunkSet.objectId(), chunkSet.documentId(), item.chunkIndex(),
                        item.chunkId(), item.text(), item.metadata(), null))
                .toList();
    }

    private String chunkingQualityStatus(int chunkCount, int ideaBlockCount, int fallbackCount) {
        if (chunkCount <= 0) {
            return "NO_CHUNKS";
        }
        if (ideaBlockCount > 0 && fallbackCount == 0) {
            return "IDEABLOCK_ONLY";
        }
        if (ideaBlockCount > 0) {
            return "IDEABLOCK_PARTIAL_FALLBACK";
        }
        if (fallbackCount > 0) {
            return "BLOCKIFY_FALLBACK_ONLY";
        }
        return "STRUCTURE_OR_DEFAULT";
    }

    @Override
    public MarkdownIdeaBlockMergePreview ideaBlockMergePreview(
            MarkdownRevision revision,
            MarkdownIdeaBlockMergePreviewOptions options) {
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable();
        if (stageStore == null || revision == null) {
            return null;
        }
        MarkdownIdeaBlockMergePreviewOptions effective =
                options == null ? MarkdownIdeaBlockMergePreviewOptions.defaults() : options;
        List<RagChunkStage> stages = stageStore.findByObject(
                "attachment", Long.toString(revision.sourceAttachmentId()), revision.documentId());
        if (stages.isEmpty()) {
            return new MarkdownIdeaBlockMergePreview(
                    revision.documentId(), revision.revisionId(), false,
                    effective.llmProvider(), effective.llmModel(), List.of());
        }
        List<MarkdownIdeaBlockSummary.MergeCandidateCluster> embeddingClusters = embeddingCandidateClusters(stages);
        List<MarkdownIdeaBlockSummary.MergeCandidateCluster> lexicalClusters = mergeCandidateClusters(stages);
        List<PreviewCluster> selected = selectPreviewClusters(
                effective.preferEmbeddingClusters() ? embeddingClusters : lexicalClusters,
                effective.preferEmbeddingClusters() ? "embedding" : "lexical",
                effective.preferEmbeddingClusters() ? lexicalClusters : embeddingClusters,
                effective.preferEmbeddingClusters() ? "lexical" : "embedding",
                effective.clusterId(),
                effective.maxClusters());
        Map<String, RagChunkStage> stagesByChunkId = new LinkedHashMap<>();
        for (RagChunkStage stage : stages) {
            stagesByChunkId.put(stage.chunkId(), stage);
        }
        AiProviderRegistry registry = aiProviderRegistryProvider == null ? null : aiProviderRegistryProvider.getIfAvailable();
        ChatPort chatPort = null;
        if (registry != null) {
            try {
                chatPort = registry.chatPort(effective.llmProvider());
            } catch (RuntimeException ex) {
                log.warn("IdeaBlock merge preview LLM is not available: {}", ex.getMessage());
            }
        }
        List<MarkdownIdeaBlockMergePreview.ClusterPreview> previews = new ArrayList<>();
        boolean llmUsed = false;
        for (PreviewCluster selectedCluster : selected) {
            List<RagChunkStage> members = selectedCluster.cluster().chunkIds().stream()
                    .map(stagesByChunkId::get)
                    .filter(stage -> stage != null)
                    .toList();
            MarkdownIdeaBlockMergePreview.ClusterPreview rejected = rejectedPreview(selectedCluster, members);
            if (rejected != null) {
                previews.add(rejected);
                continue;
            }
            if (chatPort != null) {
                MarkdownIdeaBlockMergePreview.ClusterPreview llmPreview =
                        llmPreview(chatPort, effective, selectedCluster, members);
                if (llmPreview != null) {
                    previews.add(llmPreview);
                    llmUsed = true;
                    continue;
                }
            }
            previews.add(deterministicPreview(selectedCluster, members,
                    chatPort == null ? "LLM_NOT_CONFIGURED" : "LLM_PREVIEW_FAILED"));
        }
        return new MarkdownIdeaBlockMergePreview(
                revision.documentId(),
                revision.revisionId(),
                llmUsed,
                effective.llmProvider(),
                effective.llmModel(),
                previews);
    }

    @Override
    public MarkdownIdeaBlockMergeApplyResult ideaBlockMergeApply(
            MarkdownRevision revision,
            MarkdownIdeaBlockMergeApplyOptions options) {
        if (revision == null) {
            throw new IllegalArgumentException("Markdown revision is required");
        }
        if (options == null || options.planFingerprint() == null || options.planFingerprint().isBlank()) {
            throw new IllegalArgumentException("IdeaBlock merge planFingerprint is required");
        }
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable();
        if (stageStore == null) {
            throw new IllegalStateException("RAG chunk stage store is not configured");
        }
        MarkdownIdeaBlockMergePreview preview = ideaBlockMergePreview(revision, options.previewOptions());
        MarkdownIdeaBlockMergePreview.ClusterPreview plan = preview.clusters().stream()
                .filter(cluster -> options.planFingerprint().equals(cluster.planFingerprint()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("IdeaBlock merge plan was not found or is stale"));
        if (!plan.applicable()) {
            throw new IllegalArgumentException("IdeaBlock merge plan is not applicable: "
                    + String.join(",", plan.validationWarnings()));
        }
        String objectType = "attachment";
        String objectId = Long.toString(revision.sourceAttachmentId());
        List<RagChunkStage> stages = new ArrayList<>(stageStore.findByObject(objectType, objectId, revision.documentId()));
        Map<String, RagChunkStage> stagesByChunkId = new LinkedHashMap<>();
        for (RagChunkStage stage : stages) {
            stagesByChunkId.put(stage.chunkId(), stage);
        }
        List<RagChunkStage> members = plan.mergedFromChunkIds().stream()
                .map(stagesByChunkId::get)
                .filter(stage -> stage != null)
                .toList();
        if (members.size() != plan.mergedFromChunkIds().size()) {
            throw new IllegalArgumentException("IdeaBlock merge source chunks are missing");
        }
        int beforeCount = stages.size();
        Set<String> mergedChunkIds = new LinkedHashSet<>(plan.mergedFromChunkIds());
        int insertIndex = members.stream().mapToInt(RagChunkStage::chunkIndex).min().orElse(0);
        RagChunkStage merged = mergedStage(revision, plan, members, insertIndex);
        stages.sort(Comparator.comparingInt(RagChunkStage::chunkIndex));
        List<RagChunkStage> replaced = new ArrayList<>();
        boolean inserted = false;
        for (RagChunkStage stage : stages) {
            if (mergedChunkIds.contains(stage.chunkId())) {
                if (!inserted) {
                    replaced.add(merged);
                    inserted = true;
                }
                continue;
            }
            replaced.add(stage);
        }
        if (!inserted) {
            replaced.add(merged);
        }
        List<RagChunkStage> reindexed = reindexStages(replaced);
        stageStore.replace(objectType, objectId, revision.documentId(), reindexed);
        replacePreparedChunkSetItems(revision, objectType, objectId, reindexed);
        return new MarkdownIdeaBlockMergeApplyResult(
                revision.documentId(),
                revision.revisionId(),
                plan.planId(),
                plan.planFingerprint(),
                merged.chunkId(),
                plan.mergedFromChunkIds(),
                beforeCount,
                reindexed.size(),
                null);
    }

    @Override
    public MarkdownIdeaBlockMergeUndoResult ideaBlockMergeUndo(
            MarkdownRevision revision,
            MarkdownIdeaBlockMergeUndoOptions options) {
        if (revision == null) {
            throw new IllegalArgumentException("Markdown revision is required");
        }
        if (options == null || ((options.mergedChunkId() == null || options.mergedChunkId().isBlank())
                && (options.planFingerprint() == null || options.planFingerprint().isBlank()))) {
            throw new IllegalArgumentException("mergedChunkId or planFingerprint is required");
        }
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable();
        if (stageStore == null) {
            throw new IllegalStateException("RAG chunk stage store is not configured");
        }
        String objectType = "attachment";
        String objectId = Long.toString(revision.sourceAttachmentId());
        List<RagChunkStage> stages = new ArrayList<>(stageStore.findByObject(objectType, objectId, revision.documentId()));
        RagChunkStage merged = stages.stream()
                .filter(stage -> matchesUndoTarget(stage, options))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Merged IdeaBlock stage was not found"));
        List<RagChunkStage> restored = restoredStages(merged);
        int beforeCount = stages.size();
        List<RagChunkStage> replaced = new ArrayList<>();
        for (RagChunkStage stage : stages.stream().sorted(Comparator.comparingInt(RagChunkStage::chunkIndex)).toList()) {
            if (stage.chunkId().equals(merged.chunkId())) {
                replaced.addAll(restored);
            } else {
                replaced.add(stage);
            }
        }
        List<RagChunkStage> reindexed = reindexStages(replaced);
        stageStore.replace(objectType, objectId, revision.documentId(), reindexed);
        replacePreparedChunkSetItems(revision, objectType, objectId, reindexed);
        return new MarkdownIdeaBlockMergeUndoResult(
                revision.documentId(),
                revision.revisionId(),
                merged.chunkId(),
                text(merged.metadata().get("ideaBlockDistillationFingerprint")),
                restored.stream().map(RagChunkStage::chunkId).toList(),
                beforeCount,
                reindexed.size(),
                null);
    }

    private ChunkSet stageChunks(MarkdownRevision revision, String objectType, String objectId,
            Map<String, Object> metadata, MarkdownPipelineOptions options) {
        ChunkingOrchestrator chunking = chunkingProvider.getIfAvailable();
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable();
        if (chunking == null || stageStore == null) {
            throw new IllegalStateException("Chunking pipeline is not configured");
        }
        NormalizedDocument document = normalizedDocument(revision, metadata, options);
        ChunkingContext.Builder context = document.toContextBuilder();
        applyChunkingOptions(context, options);
        List<Chunk> chunks = chunking.chunk(document, context.build());
        boolean ideaBlockStrategy = isIdeaBlockStrategy(options);
        if (ideaBlockStrategy) {
            logBlockifyChunking(revision, chunks);
            if (chunks.isEmpty()) {
                throw new IllegalStateException(
                        "%s chunking produced no chunks: revisionId=%s"
                                .formatted(chunkingStrategyLabel(options), revision.revisionId()));
            }
        }
        String sourceContentHash = nonBlank(revision.contentHash(), sha256(revision.markdownText()));
        String strategyHash = strategyHash(options);
        String chunkSetId = chunkSetId(objectType, objectId, revision, sourceContentHash, strategyHash);
        List<RagChunkStage> stages = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            Map<String, Object> stageMetadata = stageMetadata(chunk.metadata().toMap());
            if (ideaBlockStrategy) {
                stageMetadata = blockifyStageMetadata(stageMetadata, chunk,
                        ChunkingStrategyType.from(options.chunkingStrategy()));
                validateBlockifyStageMetadata(stageMetadata, chunk);
            }
            chunkMetadata.putAll(stageMetadata);
            chunkMetadata.put("chunkSetId", chunkSetId);
            chunkMetadata.put("chunkSetStrategyHash", strategyHash);
            chunkMetadata.put("ragRechunkApplied", false);
            stages.add(new RagChunkStage(objectType, objectId, revision.documentId(), index,
                    chunk.id(), chunk.content(), chunkMetadata, null));
        }
        stageStore.replace(objectType, objectId, revision.documentId(), stages);
        ChunkSet chunkSet = chunkSet(revision, objectType, objectId, metadata, options, stages,
                chunkSetId, sourceContentHash, strategyHash);
        chunkSetStore().save(chunkSet);
        if (ideaBlockStrategy) {
            verifyBlockifyStageStored(stageStore, objectType, objectId, revision.documentId(), stages.size());
        }
        return chunkSet;
    }

    private ChunkSet resolvePreparedChunkSet(
            MarkdownRevision revision,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            MarkdownPipelineOptions options) {
        ChunkSetStore store = chunkSetStore();
        var stored = store.findLatest(objectType, objectId, revision.documentId(), revision.revisionId());
        if (stored.isPresent()) {
            return stored.get();
        }
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable();
        List<RagChunkStage> stages = stageStore == null
                ? List.of()
                : stageStore.findByObject(objectType, objectId, revision.documentId());
        if (stages.isEmpty()) {
            throw new IllegalStateException(
                    "Prepared ChunkSet was not found; Markdown RAG indexing will not re-extract or rechunk");
        }
        String sourceContentHash = nonBlank(revision.contentHash(), sha256(revision.markdownText()));
        String strategyHash = strategyHash(options);
        String chunkSetId = chunkSetId(objectType, objectId, revision, sourceContentHash, strategyHash);
        ChunkSet bridged = chunkSet(revision, objectType, objectId, metadata, options, stages,
                chunkSetId, sourceContentHash, strategyHash);
        return store.save(bridged);
    }

    private void replacePreparedChunkSetItems(
            MarkdownRevision revision,
            String objectType,
            String objectId,
            List<RagChunkStage> stages) {
        ChunkSetStore store = chunkSetStore();
        store.findLatest(objectType, objectId, revision.documentId(), revision.revisionId()).ifPresent(existing -> {
            List<ChunkSetItem> items = stages.stream()
                    .sorted(Comparator.comparingInt(RagChunkStage::chunkIndex))
                    .map(stage -> new ChunkSetItem(
                            stage.chunkIndex(), stage.chunkId(), stage.text(), sha256(stage.text()), stage.metadata()))
                    .toList();
            store.save(new ChunkSet(
                    existing.chunkSetId(), existing.objectType(), existing.objectId(), existing.documentId(),
                    existing.sourceRevisionId(), existing.sourceContentHash(), existing.strategy(),
                    existing.strategyHash(), existing.chunkUnit(), existing.maxSize(), existing.overlap(),
                    existing.status(), existing.qualityStatus(), existing.qualityIssues(), existing.metadata(),
                    items, existing.createdAt(), Instant.now()));
        });
    }

    private ChunkSet chunkSet(
            MarkdownRevision revision,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            MarkdownPipelineOptions options,
            List<RagChunkStage> stages,
            String chunkSetId,
            String sourceContentHash,
            String strategyHash) {
        List<RagChunkStage> ordered = stages.stream()
                .sorted(Comparator.comparingInt(RagChunkStage::chunkIndex))
                .toList();
        Map<String, Object> artifactMetadata = new LinkedHashMap<>(metadata);
        if (!ordered.isEmpty()) {
            artifactMetadata.putAll(ordered.get(0).metadata());
        }
        artifactMetadata.put("chunkSetId", chunkSetId);
        artifactMetadata.put("chunkSetStrategyHash", strategyHash);
        artifactMetadata.put("chunkSetChunkCount", ordered.size());
        artifactMetadata.put("ragRechunkApplied", false);
        List<String> qualityIssues = qualityIssues(artifactMetadata);
        ChunkSetQualityStatus qualityStatus = reviewRequired(artifactMetadata, qualityIssues)
                ? ChunkSetQualityStatus.REVIEW_REQUIRED
                : ChunkSetQualityStatus.VALID;
        List<ChunkSetItem> items = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            RagChunkStage stage = ordered.get(index);
            Map<String, Object> itemMetadata = new LinkedHashMap<>(stage.metadata());
            itemMetadata.put("chunkSetId", chunkSetId);
            itemMetadata.put("ragRechunkApplied", false);
            items.add(new ChunkSetItem(index, stage.chunkId(), stage.text(), sha256(stage.text()), itemMetadata));
        }
        String strategy = firstNonBlank(
                text(artifactMetadata.get(ChunkMetadata.KEY_ACTUAL_CHUNKING_STRATEGY)),
                text(artifactMetadata.get(ChunkMetadata.KEY_STRATEGY)),
                options.chunkingStrategy(),
                ChunkingStrategyType.RECURSIVE.value());
        String chunkUnit = firstNonBlank(
                text(artifactMetadata.get(ChunkMetadata.KEY_CHUNK_UNIT)),
                options.chunkUnit() == null ? null : options.chunkUnit().toLowerCase());
        Instant now = Instant.now();
        return new ChunkSet(
                chunkSetId, objectType, objectId, revision.documentId(), revision.revisionId(),
                sourceContentHash, strategy, strategyHash, chunkUnit,
                options.chunkMaxSize(), options.chunkOverlap(), ChunkSetStatus.READY, qualityStatus,
                qualityIssues, artifactMetadata, items, now, now);
    }

    private ChunkSetStore chunkSetStore() {
        return chunkSetStoreProvider == null
                ? ChunkSetStore.noop()
                : chunkSetStoreProvider.getIfAvailable(ChunkSetStore::noop);
    }

    private String strategyHash(MarkdownPipelineOptions options) {
        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("strategy", options.chunkingStrategy());
        strategy.put("maxSize", options.chunkMaxSize());
        strategy.put("overlap", options.chunkOverlap());
        strategy.put("unit", options.chunkUnit());
        strategy.put("blockifyLlmProvider", options.blockifyLlmProvider());
        strategy.put("blockifyLlmModel", options.blockifyLlmModel());
        strategy.put("blockifyPiiMaskingEnabled", options.blockifyPiiMaskingEnabled());
        try {
            return sha256(objectMapper.writeValueAsString(strategy));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fingerprint ChunkSet strategy", ex);
        }
    }

    private String chunkSetId(
            String objectType,
            String objectId,
            MarkdownRevision revision,
            String sourceContentHash,
            String strategyHash) {
        String hash = sha256(String.join("|", objectType, objectId, revision.documentId(), revision.revisionId(),
                sourceContentHash, strategyHash));
        return "cset-" + hash.substring(0, 32);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private List<String> qualityIssues(Map<String, Object> metadata) {
        LinkedHashSet<String> issues = new LinkedHashSet<>();
        addIssues(issues, metadata.get("normalizationIssues"));
        addIssues(issues, metadata.get("markdownQualityIssues"));
        addIssues(issues, metadata.get(ChunkMetadata.KEY_CHUNK_QUALITY_ISSUES));
        return List.copyOf(issues);
    }

    private void addIssues(Set<String> issues, Object value) {
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                String issue = text(item);
                if (issue != null) {
                    issues.add(issue);
                }
            }
            return;
        }
        String issue = text(value);
        if (issue != null) {
            issues.add(issue);
        }
    }

    private boolean reviewRequired(Map<String, Object> metadata, List<String> issues) {
        return !issues.isEmpty()
                || "REVIEW_REQUIRED".equalsIgnoreCase(text(metadata.get("normalizationStatus")))
                || "REVIEW_REQUIRED".equalsIgnoreCase(text(metadata.get("markdownQualityStatus")))
                || "REVIEW_REQUIRED".equalsIgnoreCase(
                        text(metadata.get(ChunkMetadata.KEY_CHUNK_QUALITY_STATUS)));
    }

    private ChunkingContext.Builder chunkingContext(NormalizedDocument document, MarkdownPipelineOptions options) {
        ChunkingContext.Builder context = document.toContextBuilder();
        applyChunkingOptions(context, options);
        return context;
    }

    private void applyChunkingOptions(ChunkingContext.Builder context, MarkdownPipelineOptions options) {
        if (options.chunkingStrategy() == null) {
            context.useConfiguredStrategy();
        } else {
            context.strategy(ChunkingStrategyType.from(options.chunkingStrategy()));
        }
        if (options.chunkMaxSize() == null) {
            context.useConfiguredMaxSize();
        } else {
            context.maxSize(options.chunkMaxSize());
        }
        if (options.chunkOverlap() == null) {
            context.useConfiguredOverlap();
        } else {
            context.overlap(options.chunkOverlap());
        }
        if (options.chunkUnit() != null) {
            context.unit(ChunkUnit.valueOf(options.chunkUnit()));
        }
    }

    private Map<String, Object> stageMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(metadata);
        sanitized.remove(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT);
        sanitized.remove(ChunkMetadata.KEY_PARENT_CHUNK_BLOCK_IDS);
        sanitized.remove(ChunkMetadata.KEY_PARENT_CHUNK_SOURCE_REFS);
        // Document- and parent-wide values must not be duplicated for every child chunk.
        sanitized.remove("pdfExtractionParts");
        sanitized.remove("pageQuality");
        return sanitized;
    }

    private Map<String, Object> blockifyStageMetadata(
            Map<String, Object> metadata,
            Chunk chunk,
            ChunkingStrategyType requestedStrategy) {
        Map<String, Object> enriched = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        boolean fallbackLike = chunk.metadata().strategy() == ChunkingStrategyType.STRUCTURE_BASED
                || "structure-based".equals(text(enriched.get("actualChunkingStrategy")))
                || BLOCKIFY_VALIDATION_FALLBACK.equals(text(enriched.get("validationStatus")))
                || text(enriched.get("fallbackReason")) != null;
        if (fallbackLike) {
            enriched.putIfAbsent("requestedChunkingStrategy", requestedStrategy.value());
            enriched.putIfAbsent("actualChunkingStrategy", ChunkingStrategyType.STRUCTURE_BASED.value());
            enriched.putIfAbsent("validationStatus", BLOCKIFY_VALIDATION_FALLBACK);
            enriched.putIfAbsent("fallbackReason", BLOCKIFY_UNKNOWN_FALLBACK);
        }
        return enriched;
    }

    private void validateBlockifyStageMetadata(Map<String, Object> metadata, Chunk chunk) {
        String requested = text(metadata.get("requestedChunkingStrategy"));
        String actual = text(metadata.get("actualChunkingStrategy"));
        if (!isIdeaBlockStrategy(requested)) {
            throw invalidBlockifyChunk("missing requestedChunkingStrategy=blockify or knowledge-block", chunk,
                    metadata);
        }
        if (isIdeaBlockStrategy(actual)) {
            require(metadata, chunk, "schemaVersion");
            if (!isValidIdeaBlockSchemaVersion(text(metadata.get("schemaVersion")))) {
                throw invalidBlockifyChunk("invalid schemaVersion", chunk, metadata);
            }
            requireAny(metadata, chunk, "blockifyFingerprint", "knowledgeBlockFingerprint");
            require(metadata, chunk, "question");
            require(metadata, chunk, "answer");
            require(metadata, chunk, "sourceEvidence");
            return;
        }
        if (ChunkingStrategyType.STRUCTURE_BASED.value().equals(actual)) {
            if (!BLOCKIFY_VALIDATION_FALLBACK.equals(text(metadata.get("validationStatus")))) {
                throw invalidBlockifyChunk("missing validationStatus=FALLBACK", chunk, metadata);
            }
            require(metadata, chunk, "fallbackReason");
            return;
        }
        throw invalidBlockifyChunk("unsupported actualChunkingStrategy=" + actual, chunk, metadata);
    }

    private void require(Map<String, Object> metadata, Chunk chunk, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            throw invalidBlockifyChunk("missing " + key, chunk, metadata);
        }
        if (value instanceof String text && text.isBlank()) {
            throw invalidBlockifyChunk("blank " + key, chunk, metadata);
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            throw invalidBlockifyChunk("empty " + key, chunk, metadata);
        }
    }

    private void requireAny(Map<String, Object> metadata, Chunk chunk, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String text && text.isBlank()) {
                continue;
            }
            if (value instanceof List<?> list && list.isEmpty()) {
                continue;
            }
            return;
        }
        throw invalidBlockifyChunk("missing one of " + String.join(",", keys), chunk, metadata);
    }

    private void verifyBlockifyStageStored(
            RagChunkStageStore stageStore,
            String objectType,
            String objectId,
            String documentId,
            int expectedCount) {
        List<RagChunkStage> stored = stageStore.findByObject(objectType, objectId, documentId);
        if (stored.size() != expectedCount) {
            throw new IllegalStateException(
                    "Blockify chunk stage was not stored: objectType=%s, objectId=%s, documentId=%s, expected=%d, actual=%d"
                            .formatted(objectType, objectId, documentId, expectedCount, stored.size()));
        }
    }

    private IllegalStateException invalidBlockifyChunk(String reason, Chunk chunk, Map<String, Object> metadata) {
        return new IllegalStateException(
                "Invalid blockify chunk metadata: %s, chunkId=%s, chunkStrategy=%s, metadataKeys=%s"
                        .formatted(reason, chunk.id(), chunk.metadata().strategy(), metadata.keySet()));
    }

    private void logBlockifyChunking(MarkdownRevision revision, List<Chunk> chunks) {
        if (!log.isInfoEnabled()) {
            return;
        }
        Chunk first = chunks.isEmpty() ? null : chunks.get(0);
        log.info(
                "Markdown blockify chunking completed: revisionId={}, chunkCount={}, firstChunkStrategy={}, firstMetadataKeys={}, firstContentPrefix={}",
                revision.revisionId(),
                chunks.size(),
                first == null ? null : first.metadata().strategy(),
                first == null ? List.of() : first.metadata().toMap().keySet(),
                first == null ? "" : prefix(first.content()));
    }

    private String prefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private void addPipelineMetadata(Map<String, Object> metadata, MarkdownPipelineOptions options) {
        put(metadata, ChunkMetadata.KEY_STRATEGY, options.chunkingStrategy());
        put(metadata, ChunkMetadata.KEY_MAX_SIZE, options.chunkMaxSize());
        put(metadata, ChunkMetadata.KEY_OVERLAP, options.chunkOverlap());
        put(metadata, ChunkMetadata.KEY_CHUNK_UNIT,
                options.chunkUnit() == null ? null : options.chunkUnit().toLowerCase());
        put(metadata, "blockifyLlmProvider", options.blockifyLlmProvider());
        put(metadata, "blockifyLlmModel", options.blockifyLlmModel());
        put(metadata, "blockifyPiiMaskingEnabled", options.blockifyPiiMaskingEnabled());
        put(metadata, "embeddingProfileId", options.embeddingProfileId());
        put(metadata, "embeddingProvider", options.embeddingProvider());
        put(metadata, "embeddingModel", options.embeddingModel());
        put(metadata, "embeddingDimension", options.embeddingDimension());
    }

    private NormalizedDocument normalizedDocument(MarkdownRevision revision, Map<String, Object> metadata,
            MarkdownPipelineOptions options) {
        NormalizedDocumentSnapshot.Snapshot snapshot = normalizedSnapshot(revision);
        if (snapshot != null) {
            Map<String, Object> merged = new LinkedHashMap<>();
            merged.putAll(snapshot.document().metadata());
            merged.putAll(metadata);
            merged.put("normalizationStatus", snapshot.normalizationStatus());
            merged.put("normalizationIssues", snapshot.normalizationIssues());
            merged.put("normalizationSource", snapshot.normalizationSource());
            merged.putAll(snapshot.qualityMetrics());
            merged.put("normalizedSnapshotUsed", true);
            return NormalizedDocument.builder(revision.documentId())
                    .plainText(nonBlank(snapshot.document().plainText(), revision.markdownText()))
                    .sourceFormat(nonBlank(snapshot.document().sourceFormat(), "markdown"))
                    .filename(nonBlank(snapshot.document().filename(), revision.sourceFileName()))
                    .blocks(snapshot.document().blocks())
                    .metadata(merged)
                    .build();
        }
        Map<String, Object> fallbackMetadata = new LinkedHashMap<>(metadata);
        fallbackMetadata.put("normalizedSnapshotUsed", false);
        return NormalizedDocument.builder(revision.documentId())
                .plainText(revision.markdownText())
                .sourceFormat("markdown")
                .filename(revision.sourceFileName())
                .blocks(fallbackBlocks(revision, options))
                .metadata(fallbackMetadata)
                .build();
    }

    private NormalizedDocumentSnapshot.Snapshot normalizedSnapshot(MarkdownRevision revision) {
        List<MarkdownResource> resources = repository.findResources(revision.revisionId());
        if (resources == null || resources.isEmpty()) {
            return null;
        }
        for (MarkdownResource resource : resources) {
            var snapshot = NormalizedDocumentSnapshot.read(resource, objectMapper);
            if (snapshot.isPresent()) {
                return snapshot.get();
            }
        }
        return null;
    }

    private void assertRagIndexEligible(MarkdownRevision revision) {
        NormalizedDocumentSnapshot.Snapshot snapshot = normalizedSnapshot(revision);
        if (snapshot == null) {
            return;
        }
        Object eligible = snapshot.document().metadata().get("ragIndexEligible");
        if (!Boolean.FALSE.equals(eligible)) {
            return;
        }
        Object issues = snapshot.document().metadata().getOrDefault("markdownQualityIssues",
                snapshot.normalizationIssues());
        throw new IllegalStateException("Markdown quality gate blocked RAG indexing: " + issues);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<NormalizedBlock> fallbackBlocks(MarkdownRevision revision, MarkdownPipelineOptions options) {
        List<MarkdownLocator> all = repository.findLocators(revision.revisionId());
        boolean blockify = isIdeaBlockStrategy(options);
        boolean hasSection = all.stream()
                .anyMatch(locator -> "SECTION".equalsIgnoreCase(locator.locatorType()));
        boolean hasPageOrSlide = all.stream()
                .anyMatch(locator -> "PAGE".equalsIgnoreCase(locator.locatorType())
                        || "SLIDE".equalsIgnoreCase(locator.locatorType()));
        List<MarkdownLocator> selected = all.stream()
                .filter(locator -> blockify && hasSection
                        ? "SECTION".equalsIgnoreCase(locator.locatorType())
                        : !hasPageOrSlide
                        || "PAGE".equalsIgnoreCase(locator.locatorType())
                        || "SLIDE".equalsIgnoreCase(locator.locatorType()))
                .sorted(Comparator.comparingInt(MarkdownLocator::startOffset))
                .toList();
        if (selected.isEmpty()) {
            return List.of(NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, revision.markdownText())
                    .id(revision.revisionId())
                    .order(0)
                    .build());
        }
        List<NormalizedBlock> blocks = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            MarkdownLocator locator = selected.get(index);
            int start = Math.max(0, Math.min(locator.startOffset(), revision.markdownText().length()));
            int end = Math.max(start, Math.min(locator.endOffset(), revision.markdownText().length()));
            if (end == start) {
                continue;
            }
            String text = revision.markdownText().substring(start, end);
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("locatorType", locator.locatorType());
            if (locator.locatorNo() != null) {
                attributes.put("locatorNo", locator.locatorNo());
            }
            if (locator.title() != null && !locator.title().isBlank()) {
                attributes.put("sectionTitle", locator.title());
            }
            NormalizedBlock.Builder builder = NormalizedBlock.builder(blockType(locator, text), text)
                    .id(locator.locatorId())
                    .sourceRef(locator.sourceRef())
                    .order(index)
                    .headingPath(locator.title())
                    .metadata(attributes);
            if ("PAGE".equalsIgnoreCase(locator.locatorType())) {
                builder.page(locator.locatorNo());
            }
            if ("SLIDE".equalsIgnoreCase(locator.locatorType())) {
                builder.slide(locator.locatorNo());
            }
            blocks.add(builder.build());
        }
        return blocks;
    }

    private boolean isBlockify(MarkdownPipelineOptions options) {
        return options != null && options.chunkingStrategy() != null
                && ChunkingStrategyType.from(options.chunkingStrategy()) == ChunkingStrategyType.BLOCKIFY;
    }

    private boolean isIdeaBlockStrategy(MarkdownPipelineOptions options) {
        return options != null && isIdeaBlockStrategy(options.chunkingStrategy());
    }

    private boolean isIdeaBlockStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return false;
        }
        ChunkingStrategyType type = ChunkingStrategyType.from(strategy);
        return type == ChunkingStrategyType.BLOCKIFY || type == ChunkingStrategyType.KNOWLEDGE_BLOCK;
    }

    private boolean isValidIdeaBlockSchemaVersion(String schemaVersion) {
        return BLOCKIFY_SCHEMA_VERSION.equals(schemaVersion)
                || "knowledge-block-metadata-v1".equals(schemaVersion);
    }

    private String chunkingStrategyLabel(MarkdownPipelineOptions options) {
        if (options != null
                && options.chunkingStrategy() != null
                && ChunkingStrategyType.from(options.chunkingStrategy()) == ChunkingStrategyType.KNOWLEDGE_BLOCK) {
            return "Knowledge block";
        }
        return "Blockify";
    }

    private NormalizedBlockType blockType(MarkdownLocator locator, String text) {
        if ("PAGE".equalsIgnoreCase(locator.locatorType())) {
            return NormalizedBlockType.PAGE;
        }
        if ("SECTION".equalsIgnoreCase(locator.locatorType())) {
            return isHeadingOnlySection(locator, text) ? NormalizedBlockType.HEADING : NormalizedBlockType.DOCUMENT;
        }
        return NormalizedBlockType.DOCUMENT;
    }

    private boolean isHeadingOnlySection(MarkdownLocator locator, String text) {
        String title = locator.title() == null ? "" : locator.title().trim();
        String normalized = text == null ? "" : text
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("\\{#[^}]+}", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return true;
        }
        if (!title.isBlank()) {
            String normalizedTitle = title.replaceAll("\\s+", " ").trim();
            return normalized.equals(normalizedTitle)
                    || normalized.matches(java.util.regex.Pattern.quote(normalizedTitle) + "\\s*$");
        }
        return normalized.matches("^(제\\s*)?\\d+\\s*(장|절)$")
                || normalized.matches("^제\\s*\\d+조\\s*\\([^)]*\\)$");
    }

    private Map<String, Object> metadata(MarkdownRevision revision, String objectType, String objectId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("objectType", objectType);
        metadata.put("objectId", objectId);
        metadata.put("markdownDocumentId", revision.documentId());
        metadata.put("markdownRevisionId", revision.revisionId());
        metadata.put("sourceType", "ATTACHMENT");
        metadata.put("sourceFileId", revision.sourceAttachmentId());
        metadata.put("contentFormat", "markdown");
        put(metadata, "sourceObjectType", revision.sourceObjectType());
        put(metadata, "sourceObjectId", revision.sourceObjectId());
        put(metadata, "sourceFileName", revision.sourceFileName());
        put(metadata, "sourceFormat", revision.sourceFormat());
        return metadata;
    }

    private MarkdownIdeaBlockSummary.SampleIdeaBlock sampleIdeaBlock(
            RagChunkStage stage,
            MarkdownIdeaBlockSummary.MergeCandidateCluster embeddingCluster) {
        Map<String, Object> metadata = stage.metadata();
        return new MarkdownIdeaBlockSummary.SampleIdeaBlock(
                stage.chunkId(),
                text(metadata.get("ideaBlockName")),
                text(metadata.get("criticalQuestion")),
                text(metadata.get("trustedAnswer")),
                stringList(metadata.get("tags")),
                stringList(metadata.get("keywords")),
                text(metadata.get("entityName")),
                text(metadata.get("entityType")),
                metadata.get("sourceEvidence"),
                metadata.get("sourceBlockRange"),
                text(metadata.get("sourceSectionId")),
                doubleValue(metadata.get("confidence")),
                text(metadata.get("generatorModel")),
                text(metadata.get("promptVersion")),
                firstNonBlank(text(metadata.get("ideaBlockFingerprint")), text(metadata.get("blockifyFingerprint")),
                        text(metadata.get("fingerprint"))),
                text(metadata.get("schemaVersion")),
                text(metadata.get("requestedChunkingStrategy")),
                text(metadata.get("actualChunkingStrategy")),
                text(metadata.get("validationStatus")),
                text(metadata.get("fallbackReason")),
                metadata.get("typedFields"),
                booleanValue(metadata.get("ideaBlockMergeCandidate")),
                text(metadata.get("ideaBlockSimilarityClusterId")),
                integer(metadata.get("ideaBlockSimilarityClusterSize"), null),
                doubleValue(metadata.get("ideaBlockSimilarityMaxScore")),
                text(metadata.get("ideaBlockMergePolicy")),
                embeddingCluster == null ? null : embeddingCluster.clusterId(),
                embeddingCluster == null ? null : embeddingCluster.size(),
                embeddingCluster == null ? null : embeddingCluster.maxScore(),
                embeddingCluster == null ? null : true,
                embeddingCluster == null ? null : "candidate-only");
    }

    private Map<String, MarkdownIdeaBlockSummary.MergeCandidateCluster> clusterByChunkId(
            List<MarkdownIdeaBlockSummary.MergeCandidateCluster> clusters) {
        Map<String, MarkdownIdeaBlockSummary.MergeCandidateCluster> result = new LinkedHashMap<>();
        for (MarkdownIdeaBlockSummary.MergeCandidateCluster cluster : clusters) {
            for (String chunkId : cluster.chunkIds()) {
                result.put(chunkId, cluster);
            }
        }
        return result;
    }

    private int countIdeaBlocks(List<RagChunkStage> stages) {
        int count = 0;
        for (RagChunkStage stage : stages) {
            Map<String, Object> metadata = stage.metadata();
            if ("ideaBlock".equals(text(metadata.get("chunkType")))
                    || ChunkingStrategyType.BLOCKIFY.value().equals(text(metadata.get("actualChunkingStrategy")))) {
                count++;
            }
        }
        return count;
    }

    private String firstMetadataValue(List<RagChunkStage> stages, String key) {
        if (key == null) {
            return null;
        }
        for (RagChunkStage stage : stages) {
            String value = text(stage.metadata().get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double firstDoubleMetadataValue(List<RagChunkStage> stages, String key) {
        if (key == null) {
            return null;
        }
        for (RagChunkStage stage : stages) {
            Double value = doubleValue(stage.metadata().get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Integer> typedFieldCounts(List<RagChunkStage> stages) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RagChunkStage stage : stages) {
            Map<String, Object> metadata = stage.metadata();
            if (!"ideaBlock".equals(text(metadata.get("chunkType")))
                    && !ChunkingStrategyType.BLOCKIFY.value().equals(text(metadata.get("actualChunkingStrategy")))) {
                continue;
            }
            Object typedFields = metadata.get("typedFields");
            if (typedFields instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null
                            && !entry.getValue().toString().isBlank()) {
                        counts.merge(entry.getKey().toString(), 1, Integer::sum);
                    }
                }
                continue;
            }
            for (String key : typeSpecificFieldKeys(metadata)) {
                if (text(metadata.get(key)) != null) {
                    counts.merge(key, 1, Integer::sum);
                }
            }
        }
        return Map.copyOf(counts);
    }

    private Map<String, Double> typedFieldCoverage(Map<String, Integer> counts, int ideaBlockCount) {
        if (counts == null || counts.isEmpty() || ideaBlockCount <= 0) {
            return Map.of();
        }
        Map<String, Double> coverage = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            coverage.put(entry.getKey(), entry.getValue() / (double) ideaBlockCount);
        }
        return Map.copyOf(coverage);
    }

    private List<String> typeSpecificFieldKeys(Map<String, Object> metadata) {
        String type = firstNonBlank(text(metadata.get("detectedDocumentType")), text(metadata.get("requestedDocumentType")));
        if (type == null) {
            return List.of();
        }
        return switch (type) {
            case "policy" -> List.of("articleNo", "condition", "obligation", "prohibition", "exception", "deadline");
            case "narrative" -> List.of("character", "event", "cause", "effect", "location", "quote");
            case "manual" -> List.of("taskName", "input", "output", "warning", "nextAction");
            case "technical" -> List.of("endpoint", "command", "configurationKey", "errorCode");
            case "table-heavy" -> List.of("rowKey", "columnKey", "metricValue", "unit");
            default -> List.of();
        };
    }

    private List<PreviewCluster> selectPreviewClusters(
            List<MarkdownIdeaBlockSummary.MergeCandidateCluster> primary,
            String primaryType,
            List<MarkdownIdeaBlockSummary.MergeCandidateCluster> secondary,
            String secondaryType,
            String requestedClusterId,
            int maxClusters) {
        List<PreviewCluster> selected = new ArrayList<>();
        addPreviewClusters(selected, primary, primaryType, requestedClusterId, maxClusters);
        if (selected.isEmpty()) {
            addPreviewClusters(selected, secondary, secondaryType, requestedClusterId, maxClusters);
        }
        return selected;
    }

    private void addPreviewClusters(
            List<PreviewCluster> selected,
            List<MarkdownIdeaBlockSummary.MergeCandidateCluster> clusters,
            String type,
            String requestedClusterId,
            int maxClusters) {
        for (MarkdownIdeaBlockSummary.MergeCandidateCluster cluster : clusters) {
            if (requestedClusterId != null && !requestedClusterId.equals(cluster.clusterId())) {
                continue;
            }
            selected.add(new PreviewCluster(type, cluster));
            if (selected.size() >= maxClusters) {
                return;
            }
        }
    }

    private MarkdownIdeaBlockMergePreview.ClusterPreview rejectedPreview(
            PreviewCluster cluster,
            List<RagChunkStage> members) {
        if (members.size() < 2) {
            return deterministicPreview(cluster, members, "CLUSTER_HAS_TOO_FEW_MEMBERS");
        }
        if (members.stream().anyMatch(member -> member.metadata().get("sourceEvidence") == null)) {
            return deterministicPreview(cluster, members, "SOURCE_EVIDENCE_REQUIRED");
        }
        Set<String> entityTypes = new LinkedHashSet<>();
        for (RagChunkStage member : members) {
            String entityType = text(member.metadata().get("entityType"));
            if (entityType != null) {
                entityTypes.add(entityType);
            }
        }
        if (entityTypes.size() > 1) {
            return deterministicPreview(cluster, members, "ENTITY_TYPE_MISMATCH");
        }
        Set<String> firstFacts = null;
        for (RagChunkStage member : members) {
            Set<String> facts = factTokens(member.metadata());
            if (firstFacts == null) {
                firstFacts = facts;
            } else if (!firstFacts.equals(facts)) {
                return deterministicPreview(cluster, members, "FACT_TOKEN_MISMATCH");
            }
        }
        return null;
    }

    private MarkdownIdeaBlockMergePreview.ClusterPreview llmPreview(
            ChatPort chatPort,
            MarkdownIdeaBlockMergePreviewOptions options,
            PreviewCluster cluster,
            List<RagChunkStage> members) {
        try {
            ChatResponse response = chatPort.chat(ChatRequest.builder()
                    .model(options.llmModel())
                    .temperature(0.0d)
                    .topP(1.0d)
                    .maxOutputTokens(1200)
                    .messages(List.of(
                            ChatMessage.system("""
                                    You merge candidate IdeaBlocks for a RAG knowledge base.
                                    Use only supplied sourceEvidence. Preserve numbers, dates, durations, ratios and conditions exactly.
                                    If candidates contain different facts, say REJECT instead of merging.
                                    Return concise Korean Markdown, not prose about the task.
                                    """),
                            ChatMessage.user(mergePrompt(cluster, members))))
                    .build());
            String content = response.messages().stream()
                    .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                    .map(ChatMessage::content)
                    .findFirst()
                    .orElse(null);
            if (content == null || content.isBlank()) {
                return null;
            }
            List<String> warnings = llmPreviewWarnings(content, members);
            return clusterPreview(
                    cluster,
                    warnings.isEmpty() ? "PREVIEW" : "REJECTED",
                    warnings.isEmpty() ? "LLM_PREVIEW" : "LLM_PREVIEW_INVALID",
                    firstNonBlank(question(members.get(0)), "Merged IdeaBlock"),
                    content,
                    uniqueStringList(members, "keywords"),
                    uniqueStringList(members, "tags"),
                    sourceEvidence(members),
                    sourceBlockRanges(members),
                    "LLM_MERGE_PREVIEW",
                    content,
                    warnings);
        } catch (RuntimeException ex) {
            log.warn("IdeaBlock LLM merge preview failed for cluster {}: {}",
                    cluster.cluster().clusterId(), ex.getMessage());
            return null;
        }
    }

    private List<String> llmPreviewWarnings(String content, List<RagChunkStage> members) {
        List<String> warnings = new ArrayList<>();
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (normalized.contains("reject")) {
            warnings.add("LLM_REJECTED_MERGE");
        }
        if (!contentContainsHeading(content, "critical question")) {
            warnings.add("MISSING_CRITICAL_QUESTION_SECTION");
        }
        if (!contentContainsHeading(content, "trusted answer")) {
            warnings.add("MISSING_TRUSTED_ANSWER_SECTION");
        }
        Set<String> expectedFacts = new LinkedHashSet<>();
        for (RagChunkStage member : members) {
            expectedFacts.addAll(factTokens(member.metadata()));
        }
        for (String fact : expectedFacts) {
            if (!content.contains(fact)) {
                warnings.add("FACT_TOKEN_MISSING:" + fact);
            }
        }
        return List.copyOf(warnings);
    }

    private boolean contentContainsHeading(String content, String heading) {
        if (content == null) {
            return false;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        return normalized.contains("## " + heading) || normalized.contains("# " + heading);
    }

    private String mergePrompt(PreviewCluster cluster, List<RagChunkStage> members) {
        StringBuilder builder = new StringBuilder();
        builder.append("clusterId: ").append(cluster.cluster().clusterId()).append('\n');
        builder.append("clusterType: ").append(cluster.type()).append('\n');
        builder.append("IdeaBlocks:\n");
        for (RagChunkStage member : members) {
            Map<String, Object> metadata = member.metadata();
            builder.append("- chunkId: ").append(member.chunkId()).append('\n');
            builder.append("  criticalQuestion: ").append(question(member)).append('\n');
            builder.append("  trustedAnswer: ").append(answer(member)).append('\n');
            builder.append("  entityType: ").append(firstNonBlank(text(metadata.get("entityType")), "")).append('\n');
            builder.append("  sourceBlockRange: ").append(metadata.get("sourceBlockRange")).append('\n');
            builder.append("  sourceEvidence: ").append(metadata.get("sourceEvidence")).append('\n');
        }
        builder.append("""

                Output format:
                ## Critical Question
                ...
                ## Trusted Answer
                ...
                ## Merge Reason
                ...
                ## Merged From
                - chunkId
                """);
        return builder.toString();
    }

    private MarkdownIdeaBlockMergePreview.ClusterPreview deterministicPreview(
            PreviewCluster cluster,
            List<RagChunkStage> members,
            String reason) {
        return clusterPreview(
                cluster,
                reason.startsWith("LLM_") ? "PREVIEW" : "REJECTED",
                reason,
                members.isEmpty() ? null : question(members.get(0)),
                mergedAnswer(members),
                uniqueStringList(members, "keywords"),
                uniqueStringList(members, "tags"),
                sourceEvidence(members),
                sourceBlockRanges(members),
                reason,
                mergedPreviewText(members, reason));
    }

    private MarkdownIdeaBlockMergePreview.ClusterPreview clusterPreview(
            PreviewCluster cluster,
            String status,
            String reason,
            String criticalQuestion,
            String trustedAnswer,
            List<String> keywords,
            List<String> tags,
            Object sourceEvidence,
            List<Object> sourceBlockRanges,
            String mergeReason,
            String previewText) {
        return clusterPreview(cluster, status, reason, criticalQuestion, trustedAnswer, keywords, tags,
                sourceEvidence, sourceBlockRanges, mergeReason, previewText, List.of());
    }

    private MarkdownIdeaBlockMergePreview.ClusterPreview clusterPreview(
            PreviewCluster cluster,
            String status,
            String reason,
            String criticalQuestion,
            String trustedAnswer,
            List<String> keywords,
            List<String> tags,
            Object sourceEvidence,
            List<Object> sourceBlockRanges,
            String mergeReason,
            String previewText,
            List<String> additionalWarnings) {
        List<String> chunkIds = List.copyOf(cluster.cluster().chunkIds());
        String planId = "merge-plan:" + cluster.type() + ":" + cluster.cluster().clusterId();
        List<String> validationWarnings = validationWarnings(status, reason, additionalWarnings);
        boolean applicable = "PREVIEW".equals(status) && "LLM_PREVIEW".equals(reason)
                && validationWarnings.isEmpty();
        return new MarkdownIdeaBlockMergePreview.ClusterPreview(
                cluster.cluster().clusterId(),
                cluster.type(),
                chunkIds,
                status,
                reason,
                criticalQuestion,
                trustedAnswer,
                keywords,
                tags,
                sourceEvidence,
                sourceBlockRanges,
                mergeReason,
                previewText,
                planId,
                planFingerprint(cluster, chunkIds, status, reason, criticalQuestion, trustedAnswer, sourceBlockRanges),
                applicable,
                validationWarnings,
                chunkIds);
    }

    private RagChunkStage mergedStage(
            MarkdownRevision revision,
            MarkdownIdeaBlockMergePreview.ClusterPreview plan,
            List<RagChunkStage> members,
            int chunkIndex) {
        RagChunkStage first = members.get(0);
        Map<String, Object> metadata = new LinkedHashMap<>(first.metadata());
        metadata.put("schemaVersion", BLOCKIFY_SCHEMA_VERSION);
        metadata.put("chunkType", "ideaBlock");
        metadata.put("requestedChunkingStrategy", ChunkingStrategyType.BLOCKIFY.value());
        metadata.put("actualChunkingStrategy", ChunkingStrategyType.BLOCKIFY.value());
        metadata.put("validationStatus", "DISTILLED");
        metadata.put("ideaBlockDistilled", true);
        metadata.put("ideaBlockDistillationPlanId", plan.planId());
        metadata.put("ideaBlockDistillationFingerprint", plan.planFingerprint());
        metadata.put("ideaBlockDistilledFromChunkIds", plan.mergedFromChunkIds());
        metadata.put("ideaBlockDistillationSourceChunks", sourceChunkSnapshots(members));
        metadata.put("sourceChunkIds", plan.mergedFromChunkIds());
        put(metadata, "criticalQuestion", plan.criticalQuestion());
        put(metadata, "question", plan.criticalQuestion());
        put(metadata, "trustedAnswer", plan.trustedAnswer());
        put(metadata, "answer", plan.trustedAnswer());
        metadata.put("keywords", plan.keywords());
        metadata.put("tags", plan.tags());
        metadata.put("sourceEvidence", plan.sourceEvidence());
        metadata.put("sourceBlockRanges", plan.sourceBlockRanges());
        metadata.put("mergeReason", plan.mergeReason());
        metadata.put("planFingerprint", plan.planFingerprint());
        metadata.put("ideaBlockFingerprint", plan.planFingerprint());
        metadata.put("blockifyFingerprint", plan.planFingerprint());
        String chunkId = "ideablock-merged-" + plan.planFingerprint().replace("sha256:", "").substring(0, 16);
        String text = firstNonBlank(plan.previewText(), plan.trustedAnswer(), first.text());
        return new RagChunkStage(
                first.objectType(),
                first.objectId(),
                revision.documentId(),
                chunkIndex,
                chunkId,
                text,
                metadata,
                null);
    }

    private List<RagChunkStage> reindexStages(List<RagChunkStage> stages) {
        List<RagChunkStage> reindexed = new ArrayList<>();
        for (int index = 0; index < stages.size(); index++) {
            RagChunkStage stage = stages.get(index);
            reindexed.add(new RagChunkStage(
                    stage.objectType(),
                    stage.objectId(),
                    stage.documentId(),
                    index,
                    stage.chunkId(),
                    stage.text(),
                    stage.metadata(),
                    stage.createdAt()));
        }
        return reindexed;
    }

    private boolean matchesUndoTarget(RagChunkStage stage, MarkdownIdeaBlockMergeUndoOptions options) {
        if (options.mergedChunkId() != null && !options.mergedChunkId().isBlank()
                && options.mergedChunkId().equals(stage.chunkId())) {
            return true;
        }
        String fingerprint = text(stage.metadata().get("ideaBlockDistillationFingerprint"));
        return options.planFingerprint() != null && !options.planFingerprint().isBlank()
                && options.planFingerprint().equals(fingerprint);
    }

    private List<RagChunkStage> restoredStages(RagChunkStage merged) {
        Object snapshots = merged.metadata().get("ideaBlockDistillationSourceChunks");
        if (!(snapshots instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Merged IdeaBlock stage does not contain source chunk snapshots");
        }
        List<RagChunkStage> restored = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Invalid source chunk snapshot");
            }
            String chunkId = text(map.get("chunkId"));
            Integer chunkIndex = integer(map.get("chunkIndex"), restored.size());
            String text = text(map.get("text"));
            Map<String, Object> metadata = snapshotMetadata(map.get("metadata"));
            if (chunkId == null || text == null) {
                throw new IllegalArgumentException("Invalid source chunk snapshot");
            }
            restored.add(new RagChunkStage(
                    merged.objectType(),
                    merged.objectId(),
                    merged.documentId(),
                    chunkIndex,
                    chunkId,
                    text,
                    metadata,
                    null));
        }
        restored.sort(Comparator.comparingInt(RagChunkStage::chunkIndex));
        return restored;
    }

    private Map<String, Object> snapshotMetadata(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }

    private List<Map<String, Object>> sourceChunkSnapshots(List<RagChunkStage> members) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (RagChunkStage member : members) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("chunkId", member.chunkId());
            snapshot.put("chunkIndex", member.chunkIndex());
            snapshot.put("text", member.text());
            snapshot.put("metadata", member.metadata());
            snapshots.add(snapshot);
        }
        return List.copyOf(snapshots);
    }

    private List<String> validationWarnings(String status, String reason, List<String> additionalWarnings) {
        if (additionalWarnings != null && !additionalWarnings.isEmpty()) {
            return List.copyOf(additionalWarnings);
        }
        if ("PREVIEW".equals(status) && "LLM_PREVIEW".equals(reason)) {
            return List.of();
        }
        if (reason == null || reason.isBlank()) {
            return List.of("MERGE_PREVIEW_NOT_APPLICABLE");
        }
        return List.of(reason);
    }

    private String planFingerprint(
            PreviewCluster cluster,
            List<String> chunkIds,
            String status,
            String reason,
            String criticalQuestion,
            String trustedAnswer,
            List<Object> sourceBlockRanges) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.join("\n",
                    cluster.type(),
                    cluster.cluster().clusterId(),
                    String.join(",", chunkIds),
                    firstNonBlank(status, ""),
                    firstNonBlank(reason, ""),
                    firstNonBlank(criticalQuestion, ""),
                    firstNonBlank(trustedAnswer, ""),
                    String.valueOf(sourceBlockRanges));
            return "sha256:" + HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String mergedPreviewText(List<RagChunkStage> members, String reason) {
        return "## Critical Question\n"
                + (members.isEmpty() ? "" : question(members.get(0)))
                + "\n\n## Trusted Answer\n"
                + mergedAnswer(members)
                + "\n\n## Merge Reason\n"
                + reason;
    }

    private String mergedAnswer(List<RagChunkStage> members) {
        return members.stream()
                .map(this::answer)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private String question(RagChunkStage stage) {
        Map<String, Object> metadata = stage.metadata();
        return firstNonBlank(text(metadata.get("criticalQuestion")), text(metadata.get("question")));
    }

    private String answer(RagChunkStage stage) {
        Map<String, Object> metadata = stage.metadata();
        return firstNonBlank(text(metadata.get("trustedAnswer")), text(metadata.get("answer")), stage.text());
    }

    private List<String> uniqueStringList(List<RagChunkStage> members, String key) {
        Set<String> values = new LinkedHashSet<>();
        for (RagChunkStage member : members) {
            values.addAll(stringList(member.metadata().get(key)));
        }
        return List.copyOf(values);
    }

    private Object sourceEvidence(List<RagChunkStage> members) {
        List<Object> values = new ArrayList<>();
        for (RagChunkStage member : members) {
            Object evidence = member.metadata().get("sourceEvidence");
            if (evidence != null) {
                values.add(evidence);
            }
        }
        return values;
    }

    private List<Object> sourceBlockRanges(List<RagChunkStage> members) {
        List<Object> values = new ArrayList<>();
        for (RagChunkStage member : members) {
            Object range = member.metadata().get("sourceBlockRange");
            if (range != null) {
                values.add(range);
            }
        }
        return values;
    }

    private List<MarkdownIdeaBlockSummary.MergeCandidateCluster> mergeCandidateClusters(List<RagChunkStage> stages) {
        Map<String, List<RagChunkStage>> grouped = new LinkedHashMap<>();
        Map<String, Double> maxScores = new LinkedHashMap<>();
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (RagChunkStage stage : stages) {
            Map<String, Object> metadata = stage.metadata();
            if (!Boolean.TRUE.equals(booleanValue(metadata.get("ideaBlockMergeCandidate")))) {
                continue;
            }
            String clusterId = text(metadata.get("ideaBlockSimilarityClusterId"));
            if (clusterId == null) {
                continue;
            }
            grouped.computeIfAbsent(clusterId, ignored -> new ArrayList<>()).add(stage);
            Double score = doubleValue(metadata.get("ideaBlockSimilarityMaxScore"));
            if (score != null) {
                maxScores.merge(clusterId, score, Math::max);
            }
            sizes.merge(clusterId, integer(metadata.get("ideaBlockSimilarityClusterSize"), 0), Math::max);
        }
        List<MarkdownIdeaBlockSummary.MergeCandidateCluster> clusters = new ArrayList<>();
        for (Map.Entry<String, List<RagChunkStage>> entry : grouped.entrySet()) {
            List<String> chunkIds = entry.getValue().stream()
                    .map(RagChunkStage::chunkId)
                    .toList();
            int size = Math.max(sizes.getOrDefault(entry.getKey(), 0), chunkIds.size());
            clusters.add(new MarkdownIdeaBlockSummary.MergeCandidateCluster(
                    entry.getKey(), size, maxScores.get(entry.getKey()), chunkIds));
        }
        return clusters;
    }

    private List<MarkdownIdeaBlockSummary.MergeCandidateCluster> embeddingCandidateClusters(List<RagChunkStage> stages) {
        EmbeddingPort embeddingPort = embeddingPortProvider == null ? null : embeddingPortProvider.getIfAvailable();
        if (embeddingPort == null) {
            return List.of();
        }
        List<RagChunkStage> candidates = stages.stream()
                .filter(stage -> Boolean.TRUE.equals(booleanValue(stage.metadata().get("ideaBlockMergeCandidate"))))
                .filter(stage -> ChunkingStrategyType.BLOCKIFY.value()
                        .equals(text(stage.metadata().get("actualChunkingStrategy")))
                        || "ideaBlock".equals(text(stage.metadata().get("chunkType"))))
                .toList();
        if (candidates.size() < 2) {
            return List.of();
        }
        List<String> texts = candidates.stream()
                .map(this::embeddingClusterText)
                .toList();
        try {
            EmbeddingResponse response = embeddingPort.embed(new EmbeddingRequest(
                    texts,
                    null,
                    null,
                    EmbeddingInputType.TEXT,
                    Map.of("purpose", "markdown-ideablock-clustering")));
            List<EmbeddingVector> vectors = response.vectors();
            if (vectors.size() != candidates.size()) {
                log.warn("IdeaBlock embedding clustering skipped because vector count does not match candidates: expected={}, actual={}",
                        candidates.size(), vectors.size());
                return List.of();
            }
            return embeddingCandidateClusters(candidates, vectors);
        } catch (RuntimeException ex) {
            log.warn("IdeaBlock embedding clustering skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<MarkdownIdeaBlockSummary.MergeCandidateCluster> embeddingCandidateClusters(
            List<RagChunkStage> candidates,
            List<EmbeddingVector> vectors) {
        DisjointSet groups = new DisjointSet(candidates.size());
        double[][] scores = new double[candidates.size()][candidates.size()];
        for (int left = 0; left < candidates.size(); left++) {
            for (int right = left + 1; right < candidates.size(); right++) {
                if (!mergeSafe(candidates.get(left).metadata(), candidates.get(right).metadata())) {
                    continue;
                }
                double score = cosine(vectors.get(left).values(), vectors.get(right).values());
                scores[left][right] = score;
                scores[right][left] = score;
                if (score >= DEFAULT_EMBEDDING_SIMILARITY_THRESHOLD) {
                    groups.union(left, right);
                }
            }
        }
        Map<Integer, List<Integer>> membersByRoot = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            membersByRoot.computeIfAbsent(groups.find(index), ignored -> new ArrayList<>()).add(index);
        }
        List<MarkdownIdeaBlockSummary.MergeCandidateCluster> clusters = new ArrayList<>();
        int clusterNo = 1;
        for (List<Integer> members : membersByRoot.values()) {
            if (members.size() < 2) {
                continue;
            }
            double maxScore = 0.0d;
            for (int left : members) {
                for (int right : members) {
                    maxScore = Math.max(maxScore, scores[left][right]);
                }
            }
            List<String> chunkIds = members.stream()
                    .map(index -> candidates.get(index).chunkId())
                    .toList();
            clusters.add(new MarkdownIdeaBlockSummary.MergeCandidateCluster(
                    "emb-" + clusterNo++, members.size(), maxScore, chunkIds));
        }
        return clusters;
    }

    private String embeddingClusterText(RagChunkStage stage) {
        Map<String, Object> metadata = stage.metadata();
        return String.join("\n",
                firstNonBlank(text(metadata.get("criticalQuestion")), text(metadata.get("question")), ""),
                firstNonBlank(text(metadata.get("trustedAnswer")), text(metadata.get("answer")), ""),
                String.join(" ", stringList(metadata.get("keywords"))),
                firstNonBlank(text(metadata.get("entityName")), ""),
                firstNonBlank(text(metadata.get("entityType")), ""));
    }

    private boolean mergeSafe(Map<String, Object> left, Map<String, Object> right) {
        Set<String> leftFacts = factTokens(left);
        Set<String> rightFacts = factTokens(right);
        if (!leftFacts.equals(rightFacts)) {
            return false;
        }
        if (!compatibleScalar(left, right, "entityType")) {
            return false;
        }
        return compatibleEntityName(left, right);
    }

    private boolean compatibleScalar(Map<String, Object> left, Map<String, Object> right, String key) {
        String leftValue = normalize(text(left.get(key)));
        String rightValue = normalize(text(right.get(key)));
        return leftValue.isBlank() || rightValue.isBlank() || leftValue.equals(rightValue);
    }

    private boolean compatibleEntityName(Map<String, Object> left, Map<String, Object> right) {
        String leftValue = normalize(text(left.get("entityName")));
        String rightValue = normalize(text(right.get("entityName")));
        return leftValue.isBlank() || rightValue.isBlank()
                || leftValue.equals(rightValue)
                || leftValue.contains(rightValue)
                || rightValue.contains(leftValue);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> factTokens(Map<String, Object> metadata) {
        String value = firstNonBlank(text(metadata.get("trustedAnswer")), text(metadata.get("answer")));
        if (value == null) {
            return Set.of();
        }
        Matcher matcher = FACT_TOKEN.matcher(value);
        Set<String> tokens = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group().replaceAll("\\s+", "");
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < left.size(); index++) {
            double leftValue = left.get(index) == null ? 0.0d : left.get(index);
            double rightValue = right.get(index) == null ? 0.0d : right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<Integer> missingSourceBlocks(List<RagChunkStage> stages) {
        Set<Integer> target = new LinkedHashSet<>();
        Set<Integer> covered = new LinkedHashSet<>();
        for (RagChunkStage stage : stages) {
            Map<String, Object> metadata = stage.metadata();
            addRange(target, metadata.get("sourceBlockRange"));
            if (ChunkingStrategyType.BLOCKIFY.value().equals(text(metadata.get("actualChunkingStrategy")))
                    || ChunkingStrategyType.STRUCTURE_BASED.value().equals(text(metadata.get("actualChunkingStrategy")))
                    || BLOCKIFY_VALIDATION_FALLBACK.equals(text(metadata.get("validationStatus")))) {
                addRange(covered, metadata.get("sourceBlockRange"));
            }
        }
        target.removeAll(covered);
        return target.stream().sorted().toList();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item == null ? null : item.toString().trim())
                    .filter(item -> item != null && !item.isBlank())
                    .toList();
        }
        String text = text(value);
        return text == null ? List.of() : List.of(text);
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value);
        return text == null ? null : Boolean.valueOf(text);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            metadata.put(key, value);
        }
    }

    private void addRange(Set<Integer> values, Object range) {
        if (!(range instanceof Map<?, ?> map)) {
            return;
        }
        Integer start = integer(map.get("start"), null);
        Integer end = integer(map.get("end"), null);
        if (start == null || end == null) {
            return;
        }
        int from = Math.min(start, end);
        int to = Math.max(start, end);
        for (int value = from; value <= to; value++) {
            values.add(value);
        }
    }

    private Integer integer(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static final class DisjointSet {
        private final int[] parents;

        private DisjointSet(int size) {
            this.parents = new int[size];
            for (int index = 0; index < size; index++) {
                parents[index] = index;
            }
        }

        private int find(int value) {
            if (parents[value] != value) {
                parents[value] = find(parents[value]);
            }
            return parents[value];
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot != rightRoot) {
                parents[rightRoot] = leftRoot;
            }
        }
    }

    private record PreviewCluster(
            String type,
            MarkdownIdeaBlockSummary.MergeCandidateCluster cluster) {
    }
}
