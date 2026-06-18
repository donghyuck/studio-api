package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexRequest;
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
import studio.one.platform.markdown.application.port.MarkdownPipelinePort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;

public class MarkdownDownstreamPipelineAdapter implements MarkdownPipelinePort {
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
            RagIndexRequest indexRequest = new RagIndexRequest(
                    revision.documentId(), revision.markdownText(), metadata, List.of(),
                    options.useLlmKeywordExtraction(),
                    options.embeddingProfileId(), options.embeddingProvider(), options.embeddingModel(),
                    options.embeddingDimension());
            RagIndexJob job = ragJobService.createJob(new RagIndexJobCreateRequest(
                    objectType, objectId, revision.documentId(), "markdown-revision",
                    true, indexRequest, revision.sourceFileName()));
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
                .blocks(blocks(revision))
                .metadata(metadata)
                .build();
        ChunkingContext.Builder context = document.toContextBuilder();
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
        List<Chunk> chunks = chunking.chunk(document, context.build());
        List<RagChunkStage> stages = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            chunkMetadata.putAll(chunk.metadata().toMap());
            stages.add(new RagChunkStage(objectType, objectId, revision.documentId(), index,
                    chunk.id(), chunk.content(), chunkMetadata, null));
        }
        stageStore.replace(objectType, objectId, revision.documentId(), stages);
    }

    private void addPipelineMetadata(Map<String, Object> metadata, MarkdownPipelineOptions options) {
        put(metadata, ChunkMetadata.KEY_STRATEGY, options.chunkingStrategy());
        put(metadata, ChunkMetadata.KEY_MAX_SIZE, options.chunkMaxSize());
        put(metadata, ChunkMetadata.KEY_OVERLAP, options.chunkOverlap());
        put(metadata, ChunkMetadata.KEY_CHUNK_UNIT,
                options.chunkUnit() == null ? null : options.chunkUnit().toLowerCase());
        put(metadata, "embeddingProfileId", options.embeddingProfileId());
        put(metadata, "embeddingProvider", options.embeddingProvider());
        put(metadata, "embeddingModel", options.embeddingModel());
        put(metadata, "embeddingDimension", options.embeddingDimension());
    }

    private List<NormalizedBlock> blocks(MarkdownRevision revision) {
        List<MarkdownLocator> all = repository.findLocators(revision.revisionId());
        boolean hasPageOrSlide = all.stream()
                .anyMatch(locator -> "PAGE".equalsIgnoreCase(locator.locatorType())
                        || "SLIDE".equalsIgnoreCase(locator.locatorType()));
        List<MarkdownLocator> selected = all.stream()
                .filter(locator -> !hasPageOrSlide
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
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("locatorType", locator.locatorType());
            if (locator.locatorNo() != null) {
                attributes.put("locatorNo", locator.locatorNo());
            }
            if (locator.title() != null && !locator.title().isBlank()) {
                attributes.put("sectionTitle", locator.title());
            }
            NormalizedBlock.Builder builder = NormalizedBlock.builder(blockType(locator), revision.markdownText()
                            .substring(start, end))
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

    private NormalizedBlockType blockType(MarkdownLocator locator) {
        if ("PAGE".equalsIgnoreCase(locator.locatorType())) {
            return NormalizedBlockType.PAGE;
        }
        if ("SECTION".equalsIgnoreCase(locator.locatorType())) {
            return NormalizedBlockType.HEADING;
        }
        return NormalizedBlockType.DOCUMENT;
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
}
