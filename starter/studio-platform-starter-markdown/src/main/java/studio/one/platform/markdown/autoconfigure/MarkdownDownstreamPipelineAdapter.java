package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.MarkdownPipelineProgress;
import studio.one.platform.markdown.application.port.MarkdownPipelinePort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;

public class MarkdownDownstreamPipelineAdapter implements MarkdownPipelinePort {
    private static final Logger log = LoggerFactory.getLogger(MarkdownDownstreamPipelineAdapter.class);
    private static final String BLOCKIFY_SCHEMA_VERSION = "blockify-metadata-v1";
    private static final String BLOCKIFY_VALIDATION_FALLBACK = "FALLBACK";
    private static final String BLOCKIFY_UNKNOWN_FALLBACK = "UNKNOWN_FALLBACK";
    private static final String REQUIRE_RAG_CHUNK_STAGE = "requireRagChunkStage";

    private final ObjectProvider<RagIndexJobService> ragJobServiceProvider;
    private final ObjectProvider<SkillRagExtractionJobService> skillJobServiceProvider;
    private final ObjectProvider<ChunkingOrchestrator> chunkingProvider;
    private final ObjectProvider<RagChunkStageStore> chunkStageStoreProvider;
    private final MarkdownRepository repository;

    public MarkdownDownstreamPipelineAdapter(ObjectProvider<RagIndexJobService> ragJobServiceProvider,
            ObjectProvider<SkillRagExtractionJobService> skillJobServiceProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            MarkdownRepository repository) {
        this.ragJobServiceProvider = ragJobServiceProvider;
        this.skillJobServiceProvider = skillJobServiceProvider;
        this.chunkingProvider = chunkingProvider;
        this.chunkStageStoreProvider = chunkStageStoreProvider;
        this.repository = repository;
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
        if (options.runChunking() && fromStage.ordinal() <= MarkdownPipelineStage.CHUNKING.ordinal()) {
            stageChunks(revision, objectType, objectId, metadata, options);
            stageCompleted.accept(MarkdownPipelineStage.CHUNKING);
        }
        if (options.runRagIndex() && fromStage.ordinal() <= MarkdownPipelineStage.RAG_INDEX.ordinal()) {
            RagIndexJobService ragJobService = ragJobServiceProvider.getIfAvailable();
            if (ragJobService == null) {
                throw new IllegalStateException("RAG index job service is not configured");
            }
            Map<String, Object> ragMetadata = new HashMap<>(metadata);
            if (isBlockify(options)) {
                ragMetadata.put(REQUIRE_RAG_CHUNK_STAGE, true);
            }
            RagIndexJob job = ragJobService.createJob(new RagIndexJobCreateRequest(
                    objectType, objectId, revision.documentId(), objectType,
                    true, null, revision.sourceFileName()),
                    new RagIndexJobSourceRequest(
                            ragMetadata, List.of(), options.useLlmKeywordExtraction(),
                            options.embeddingProfileId(), options.embeddingProvider(), options.embeddingModel()));
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
        if (options != null && "blockify".equalsIgnoreCase(options.chunkingStrategy())) {
            return MarkdownPipelinePort.super.estimateChunkCount(revision, options);
        }
        String objectType = "attachment";
        String objectId = Long.toString(revision.sourceAttachmentId());
        Map<String, Object> metadata = metadata(revision, objectType, objectId);
        addPipelineMetadata(metadata, options);
        NormalizedDocument document = NormalizedDocument.builder(revision.documentId())
                .plainText(revision.markdownText())
                .sourceFormat("markdown")
                .filename(revision.sourceFileName())
                .blocks(blocks(revision, options))
                .metadata(metadata)
                .build();
        return chunking.chunk(document, chunkingContext(document, options).build()).size();
    }

    @Override
    public MarkdownPipelineProgress.RagProgress latestRagProgress(MarkdownRevision revision) {
        RagIndexJobService ragJobService = ragJobServiceProvider.getIfAvailable();
        if (ragJobService == null || revision == null) {
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

    private void stageChunks(MarkdownRevision revision, String objectType, String objectId,
            Map<String, Object> metadata, MarkdownPipelineOptions options) {
        ChunkingOrchestrator chunking = chunkingProvider.getIfAvailable();
        RagChunkStageStore stageStore = chunkStageStoreProvider.getIfAvailable();
        if (chunking == null || stageStore == null) {
            throw new IllegalStateException("Chunking pipeline is not configured");
        }
        NormalizedDocument document = NormalizedDocument.builder(revision.documentId())
                .plainText(revision.markdownText())
                .sourceFormat("markdown")
                .filename(revision.sourceFileName())
                .blocks(blocks(revision, options))
                .metadata(metadata)
                .build();
        ChunkingContext.Builder context = document.toContextBuilder();
        applyChunkingOptions(context, options);
        List<Chunk> chunks = chunking.chunk(document, context.build());
        boolean blockify = isBlockify(options);
        if (blockify) {
            logBlockifyChunking(revision, chunks);
            if (chunks.isEmpty()) {
                throw new IllegalStateException(
                        "Blockify chunking produced no chunks: revisionId=%s".formatted(revision.revisionId()));
            }
        }
        List<RagChunkStage> stages = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            Map<String, Object> stageMetadata = stageMetadata(chunk.metadata().toMap());
            if (blockify) {
                stageMetadata = blockifyStageMetadata(stageMetadata, chunk);
                validateBlockifyStageMetadata(stageMetadata, chunk);
            }
            chunkMetadata.putAll(stageMetadata);
            stages.add(new RagChunkStage(objectType, objectId, revision.documentId(), index,
                    chunk.id(), chunk.content(), chunkMetadata, null));
        }
        stageStore.replace(objectType, objectId, revision.documentId(), stages);
        if (blockify) {
            verifyBlockifyStageStored(stageStore, objectType, objectId, revision.documentId(), stages.size());
        }
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
        return sanitized;
    }

    private Map<String, Object> blockifyStageMetadata(Map<String, Object> metadata, Chunk chunk) {
        Map<String, Object> enriched = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        boolean fallbackLike = chunk.metadata().strategy() == ChunkingStrategyType.STRUCTURE_BASED
                || "structure-based".equals(text(enriched.get("actualChunkingStrategy")))
                || BLOCKIFY_VALIDATION_FALLBACK.equals(text(enriched.get("validationStatus")))
                || text(enriched.get("fallbackReason")) != null;
        if (fallbackLike) {
            enriched.putIfAbsent("requestedChunkingStrategy", ChunkingStrategyType.BLOCKIFY.value());
            enriched.putIfAbsent("actualChunkingStrategy", ChunkingStrategyType.STRUCTURE_BASED.value());
            enriched.putIfAbsent("validationStatus", BLOCKIFY_VALIDATION_FALLBACK);
            enriched.putIfAbsent("fallbackReason", BLOCKIFY_UNKNOWN_FALLBACK);
        }
        return enriched;
    }

    private void validateBlockifyStageMetadata(Map<String, Object> metadata, Chunk chunk) {
        String requested = text(metadata.get("requestedChunkingStrategy"));
        String actual = text(metadata.get("actualChunkingStrategy"));
        if (!ChunkingStrategyType.BLOCKIFY.value().equals(requested)) {
            throw invalidBlockifyChunk("missing requestedChunkingStrategy=blockify", chunk, metadata);
        }
        if (ChunkingStrategyType.BLOCKIFY.value().equals(actual)) {
            require(metadata, chunk, "schemaVersion");
            if (!BLOCKIFY_SCHEMA_VERSION.equals(text(metadata.get("schemaVersion")))) {
                throw invalidBlockifyChunk("invalid schemaVersion", chunk, metadata);
            }
            require(metadata, chunk, "blockifyFingerprint");
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
        put(metadata, "embeddingProfileId", options.embeddingProfileId());
        put(metadata, "embeddingProvider", options.embeddingProvider());
        put(metadata, "embeddingModel", options.embeddingModel());
        put(metadata, "embeddingDimension", options.embeddingDimension());
    }

    private List<NormalizedBlock> blocks(MarkdownRevision revision, MarkdownPipelineOptions options) {
        List<MarkdownLocator> all = repository.findLocators(revision.revisionId());
        boolean blockify = isBlockify(options);
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

    private void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            metadata.put(key, value);
        }
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
