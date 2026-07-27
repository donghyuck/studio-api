package studio.one.application.web.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexJobStep;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagEmbeddingSelection;
import studio.one.platform.ai.service.pipeline.RagChunkStage;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.ResolvedRagEmbedding;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.artifact.ChunkSet;
import studio.one.platform.chunking.artifact.ChunkSetItem;
import studio.one.platform.chunking.artifact.ChunkSetStore;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;

@Component
@ConditionalOnMissingBean(AttachmentStructuredRagIndexer.class)
@ConditionalOnClass(name = {
        "studio.one.platform.chunking.core.ChunkingOrchestrator",
        "studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter",
        "studio.one.platform.textract.domain.model.ParsedFile"
})
public class DefaultAttachmentStructuredRagIndexer implements AttachmentStructuredRagIndexer {

    public static final int DEFAULT_INDEX_EMBEDDING_BATCH_SIZE = RagPipelineOptions.DEFAULT_INDEX_EMBEDDING_BATCH_SIZE;
    public static final int DEFAULT_INDEX_UPSERT_BATCH_SIZE = RagPipelineOptions.DEFAULT_INDEX_UPSERT_BATCH_SIZE;
    private static final int EMBEDDING_MAX_ATTEMPTS = 3;
    private static final long EMBEDDING_RETRY_BACKOFF_MS = 1_000L;

    private final ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider;
    private final ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;
    private final ObjectProvider<RagEmbeddingProfileResolver> embeddingProfileResolverProvider;
    private final ObjectProvider<VectorStorePort> vectorStoreProvider;
    private final ObjectProvider<RagChunkStageStore> chunkStageStoreProvider;
    private final ObjectProvider<ChunkSetStore> chunkSetStoreProvider;
    private final int indexEmbeddingBatchSize;
    private final int indexUpsertBatchSize;
    private final ThreadLocal<AttachmentRagIndexDiagnostics> latestDiagnostics = new ThreadLocal<>();

    public DefaultAttachmentStructuredRagIndexer(
            ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<VectorStorePort> vectorStoreProvider) {
        this(normalizedDocumentAdapterProvider, chunkingOrchestratorProvider, embeddingPortProvider, null, vectorStoreProvider, null);
    }

    @Autowired
    public DefaultAttachmentStructuredRagIndexer(
            ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<RagEmbeddingProfileResolver> embeddingProfileResolverProvider,
            ObjectProvider<VectorStorePort> vectorStoreProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider) {
        this(normalizedDocumentAdapterProvider, chunkingOrchestratorProvider, embeddingPortProvider,
                embeddingProfileResolverProvider, vectorStoreProvider, chunkStageStoreProvider, null,
                DEFAULT_INDEX_EMBEDDING_BATCH_SIZE, DEFAULT_INDEX_UPSERT_BATCH_SIZE);
    }

    public DefaultAttachmentStructuredRagIndexer(
            ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<RagEmbeddingProfileResolver> embeddingProfileResolverProvider,
            ObjectProvider<VectorStorePort> vectorStoreProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            int indexUpsertBatchSize) {
        this(normalizedDocumentAdapterProvider, chunkingOrchestratorProvider, embeddingPortProvider,
                embeddingProfileResolverProvider, vectorStoreProvider, chunkStageStoreProvider, null,
                DEFAULT_INDEX_EMBEDDING_BATCH_SIZE, indexUpsertBatchSize);
    }

    public DefaultAttachmentStructuredRagIndexer(
            ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<RagEmbeddingProfileResolver> embeddingProfileResolverProvider,
            ObjectProvider<VectorStorePort> vectorStoreProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            int indexEmbeddingBatchSize,
            int indexUpsertBatchSize) {
        this(normalizedDocumentAdapterProvider, chunkingOrchestratorProvider, embeddingPortProvider,
                embeddingProfileResolverProvider, vectorStoreProvider, chunkStageStoreProvider, null,
                indexEmbeddingBatchSize, indexUpsertBatchSize);
    }

    public DefaultAttachmentStructuredRagIndexer(
            ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<RagEmbeddingProfileResolver> embeddingProfileResolverProvider,
            ObjectProvider<VectorStorePort> vectorStoreProvider,
            ObjectProvider<RagChunkStageStore> chunkStageStoreProvider,
            ObjectProvider<ChunkSetStore> chunkSetStoreProvider,
            @Value("${studio.ai.rag.indexing.embedding-batch-size:10}") int indexEmbeddingBatchSize,
            @Value("${studio.ai.rag.indexing.upsert-batch-size:10}") int indexUpsertBatchSize) {
        this.normalizedDocumentAdapterProvider = normalizedDocumentAdapterProvider;
        this.chunkingOrchestratorProvider = chunkingOrchestratorProvider;
        this.embeddingPortProvider = embeddingPortProvider;
        this.embeddingProfileResolverProvider = embeddingProfileResolverProvider;
        this.vectorStoreProvider = vectorStoreProvider;
        this.chunkStageStoreProvider = chunkStageStoreProvider;
        this.chunkSetStoreProvider = chunkSetStoreProvider;
        this.indexEmbeddingBatchSize = Math.max(1, indexEmbeddingBatchSize);
        this.indexUpsertBatchSize = Math.max(1, indexUpsertBatchSize);
    }

    @Override
    public boolean index(Attachment attachment,
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            FileContentExtractionService extractor,
            InputStream inputStream) throws IOException {
        return index(attachment, documentId, objectType, objectId, metadata, extractor, inputStream,
                RagIndexProgressListener.noop());
    }

    @Override
    public boolean index(Attachment attachment,
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            FileContentExtractionService extractor,
            InputStream inputStream,
            RagIndexProgressListener listener) throws IOException {
        latestDiagnostics.remove();
        RagIndexProgressListener progress = listener == null ? RagIndexProgressListener.noop() : listener;
        TextractNormalizedDocumentAdapter adapter = normalizedDocumentAdapterProvider.getIfAvailable();
        ChunkingOrchestrator chunkingOrchestrator = chunkingOrchestratorProvider.getIfAvailable();
        EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
        VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
        RagChunkStageStore chunkStageStore = chunkStageStoreProvider == null
                ? RagChunkStageStore.noop()
                : chunkStageStoreProvider.getIfAvailable(RagChunkStageStore::noop);
        ChunkSet preparedChunkSet = preparedChunkSet(metadata, objectType, objectId, documentId);
        List<Chunk> chunks = preparedChunkSet == null
                ? List.of()
                : preparedChunks(preparedChunkSet);
        boolean restoredFromPrepared = preparedChunkSet != null;
        if (chunks.isEmpty() && hasObjectScope(objectType, objectId)) {
            chunks = stagedChunks(chunkStageStore, objectType, objectId, documentId);
        }
        boolean restoredFromStage = !restoredFromPrepared && !chunks.isEmpty();
        String fallbackReason = chunks.isEmpty() && !restoredFromPrepared
                ? fallbackReason(adapter, chunkingOrchestrator, embeddingPort, vectorStore, objectType, objectId)
                : stagedFallbackReason(embeddingPort, vectorStore, objectType, objectId);
        if (fallbackReason != null) {
            latestDiagnostics.set(AttachmentRagIndexDiagnostics.fallback(fallbackReason));
            return false;
        }

        int parsedBlockCount = 0;
        if (!restoredFromStage && !restoredFromPrepared) {
            progress.onStep(RagIndexJobStep.EXTRACTING);
            ParsedFile parsedFile = extractor.parseStructured(attachment.getContentType(), attachment.getName(), inputStream);
            parsedBlockCount = parsedFile.blocks().size();
            NormalizedDocument normalizedDocument = adapter.adapt(documentId, parsedFile);
            NormalizedDocument document = enrichMetadata(documentId, normalizedDocument, metadata);
            progress.onStep(RagIndexJobStep.CHUNKING);
            chunks = chunkingOrchestrator.chunk(document);
            saveChunkStage(chunkStageStore, objectType, objectId, documentId, chunks);
        } else {
            progress.onInfo(
                    RagIndexJobStep.CHUNKING,
                    restoredFromPrepared ? "Attachment RAG ChunkSet restored" : "Attachment RAG chunk stage restored",
                    "objectType=%s, objectId=%s, documentId=%s, chunkSetId=%s, chunkCount=%d"
                            .formatted(objectType, objectId, documentId,
                                    preparedChunkSet == null ? null : preparedChunkSet.chunkSetId(), chunks.size()));
        }
        progress.onChunkCount(chunks.size());
        if (chunks.isEmpty()) {
            latestDiagnostics.set(AttachmentRagIndexDiagnostics.structured(parsedBlockCount, 0, 0));
            progress.onStep(RagIndexJobStep.INDEXING);
            vectorStore.replaceRecordsByObject(objectType, objectId, List.of());
            chunkStageStore.deleteByObject(objectType, objectId, documentId);
            progress.onIndexedCount(0);
            return true;
        }

        int vectorCount;
        if (!restoredFromStage && !restoredFromPrepared && chunks.size() <= indexUpsertBatchSize) {
            List<VectorRecord> records = embedRecords(
                    documentId,
                    objectType,
                    objectId,
                    metadata,
                    embeddingPort,
                    chunks,
                    progress);
            progress.onStep(RagIndexJobStep.INDEXING);
            vectorStore.replaceRecordsByObject(objectType, objectId, records);
            vectorCount = records.size();
        } else {
            vectorCount = embedAndUpsertInBatches(
                    documentId,
                    objectType,
                    objectId,
                    metadata,
                    embeddingPort,
                    chunks,
                    vectorStore,
                    progress,
                    restoredFromStage || restoredFromPrepared);
        }
        chunkStageStore.deleteByObject(objectType, objectId, documentId);
        progress.onIndexedCount(vectorCount);
        latestDiagnostics.set(AttachmentRagIndexDiagnostics.structured(
                parsedBlockCount,
                chunks.size(),
                vectorCount));
        return true;
    }

    private ChunkSet preparedChunkSet(
            Map<String, Object> metadata,
            String objectType,
            String objectId,
            String documentId) {
        String chunkSetId = text(metadata == null ? null : metadata.get("chunkSetId"));
        boolean required = booleanValue(metadata == null ? null : metadata.get("requirePreparedChunks"));
        if (chunkSetId == null) {
            if (required) {
                throw new IllegalStateException("chunkSetId is required for prepared-chunk RAG indexing");
            }
            return null;
        }
        ChunkSetStore store = chunkSetStoreProvider == null ? null : chunkSetStoreProvider.getIfAvailable();
        if (store == null) {
            if (required) {
                throw new IllegalStateException("ChunkSet store is not configured");
            }
            return null;
        }
        ChunkSet chunkSet = store.findById(chunkSetId).orElse(null);
        if (chunkSet == null) {
            if (required) {
                throw new IllegalStateException("Prepared ChunkSet was not found: " + chunkSetId);
            }
            return null;
        }
        if (!Objects.equals(chunkSet.objectType(), objectType)
                || !Objects.equals(chunkSet.objectId(), objectId)
                || !Objects.equals(chunkSet.documentId(), documentId)) {
            throw new IllegalStateException("Prepared ChunkSet scope does not match the RAG index request");
        }
        if (!chunkSet.indexEligible()) {
            throw new IllegalStateException("Prepared ChunkSet is not eligible for RAG indexing: " + chunkSetId);
        }
        return chunkSet;
    }

    private List<Chunk> preparedChunks(ChunkSet chunkSet) {
        return chunkSet.items().stream()
                .map(item -> preparedChunk(chunkSet, item))
                .toList();
    }

    private Chunk preparedChunk(ChunkSet chunkSet, ChunkSetItem item) {
        Map<String, Object> metadata = new HashMap<>(item.metadata());
        metadata.put("chunkSetId", chunkSet.chunkSetId());
        metadata.put("chunkSetStrategyHash", chunkSet.strategyHash());
        metadata.put("ragRechunkApplied", false);
        return Chunk.of(
                item.chunkId(),
                item.text(),
                ChunkMetadata.builder(
                                ChunkingStrategyType.from(firstText(
                                        metadata.get(ChunkMetadata.KEY_STRATEGY), chunkSet.strategy())),
                                item.chunkIndex())
                        .chunkType(ChunkType.from(text(metadata.get(ChunkMetadata.KEY_CHUNK_TYPE))))
                        .attributes(metadata)
                        .build());
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool
                ? bool
                : value instanceof String string && Boolean.parseBoolean(string);
    }

    private String firstText(Object first, Object second) {
        String value = text(first);
        return value == null ? text(second) : value;
    }

    private List<Chunk> stagedChunks(
            RagChunkStageStore chunkStageStore,
            String objectType,
            String objectId,
            String documentId) {
        return chunkStageStore.findByObject(objectType, objectId, documentId).stream()
                .map(stage -> Chunk.of(
                        stage.chunkId(),
                        stage.text(),
                        ChunkMetadata.builder(
                                ChunkingStrategyType.from(text(stage.metadata().get(ChunkMetadata.KEY_STRATEGY))),
                                stage.chunkIndex())
                                .chunkType(ChunkType.from(text(stage.metadata().get(ChunkMetadata.KEY_CHUNK_TYPE))))
                                .attributes(stage.metadata())
                                .build()))
                .toList();
    }

    private void saveChunkStage(
            RagChunkStageStore chunkStageStore,
            String objectType,
            String objectId,
            String documentId,
            List<Chunk> chunks) {
        List<RagChunkStage> stages = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            Integer chunkIndex = chunkIndex(chunk);
            stages.add(new RagChunkStage(
                    objectType,
                    objectId,
                    documentId,
                    chunkIndex == null ? stages.size() : chunkIndex,
                    chunk.id(),
                    chunk.content(),
                    chunk.metadata().toMap(),
                    null));
        }
        chunkStageStore.replace(objectType, objectId, documentId, stages);
    }

    private List<VectorRecord> embedRecords(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            EmbeddingPort embeddingPort,
            List<Chunk> chunks,
            RagIndexProgressListener progress) {
        progress.onStep(RagIndexJobStep.EMBEDDING);
        List<VectorRecord> records = new ArrayList<>(chunks.size());
        int embedded = 0;
        for (int index = 0; index < chunks.size();) {
            List<PendingChunk> batch = compatibleEmbeddingBatch(
                    documentId,
                    objectType,
                    objectId,
                    metadata,
                    embeddingPort,
                    chunks,
                    index);
            records.addAll(embedPendingBatch(batch));
            embedded += batch.size();
            progress.onEmbeddedCount(embedded);
            index += batch.size();
        }
        return records;
    }

    private int embedAndUpsertInBatches(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            EmbeddingPort embeddingPort,
            List<Chunk> chunks,
            VectorStorePort vectorStore,
            RagIndexProgressListener progress,
            boolean replaceExistingObject) {
        progress.onStep(RagIndexJobStep.INDEXING);
        if (replaceExistingObject) {
            vectorStore.deleteByObject(objectType, objectId);
        }
        Set<Integer> completedChunkIndexes = replaceExistingObject
                ? Set.of()
                : completedChunkIndexes(vectorStore, objectType, objectId, metadata);
        if (!replaceExistingObject && completedChunkIndexes.isEmpty()) {
            vectorStore.deleteByObject(objectType, objectId);
        }
        progress.onStep(RagIndexJobStep.EMBEDDING);
        List<VectorRecord> batch = new ArrayList<>(Math.min(indexUpsertBatchSize, chunks.size()));
        int embedded = 0;
        int indexed = completedChunkIndexes.size();
        for (int index = 0; index < chunks.size();) {
            Chunk chunk = chunks.get(index);
            if (completedChunkIndexes.contains(chunkIndex(chunk))) {
                embedded++;
                progress.onEmbeddedCount(embedded);
                progress.onIndexedCount(indexed);
                index++;
                continue;
            }
            List<PendingChunk> embeddingBatch = compatibleEmbeddingBatch(
                    documentId,
                    objectType,
                    objectId,
                    metadata,
                    embeddingPort,
                    chunks,
                    index,
                    completedChunkIndexes);
            for (VectorRecord record : embedPendingBatch(embeddingBatch)) {
                batch.add(record);
                embedded++;
                progress.onEmbeddedCount(embedded);
                if (batch.size() >= indexUpsertBatchSize) {
                    progress.onStep(RagIndexJobStep.INDEXING);
                    upsertBatch(vectorStore, batch, indexed, progress);
                    indexed += batch.size();
                    batch.clear();
                    progress.onIndexedCount(indexed);
                    progress.onStep(RagIndexJobStep.EMBEDDING);
                }
            }
            index += embeddingBatch.size();
        }
        if (!batch.isEmpty()) {
            progress.onStep(RagIndexJobStep.INDEXING);
            upsertBatch(vectorStore, batch, indexed, progress);
            indexed += batch.size();
            progress.onIndexedCount(indexed);
        }
        return indexed;
    }

    private void upsertBatch(
            VectorStorePort vectorStore,
            List<VectorRecord> batch,
            int indexedBeforeBatch,
            RagIndexProgressListener progress) {
        int fromChunkIndex = chunkIndex(batch.get(0), indexedBeforeBatch);
        int toChunkIndex = chunkIndex(batch.get(batch.size() - 1), indexedBeforeBatch + batch.size() - 1);
        try {
            vectorStore.upsertAll(List.copyOf(batch));
        } catch (RuntimeException ex) {
            String detail = "chunkRange=%d-%d, batchSize=%d, configuredBatchSize=%d, error=%s"
                    .formatted(fromChunkIndex, toChunkIndex, batch.size(), indexUpsertBatchSize, ex.getMessage());
            progress.onError(
                    RagIndexJobStep.INDEXING,
                    RagIndexJobLogCode.VECTOR_UPSERT_FAILED,
                    "Attachment RAG vector upsert failed",
                    detail);
            throw ex;
        }
    }

    private List<PendingChunk> compatibleEmbeddingBatch(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            EmbeddingPort embeddingPort,
            List<Chunk> chunks,
            int startIndex) {
        return compatibleEmbeddingBatch(documentId, objectType, objectId, metadata, embeddingPort, chunks, startIndex, Set.of());
    }

    private List<PendingChunk> compatibleEmbeddingBatch(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            EmbeddingPort embeddingPort,
            List<Chunk> chunks,
            int startIndex,
            Set<Integer> completedChunkIndexes) {
        List<PendingChunk> batch = new ArrayList<>(Math.min(indexEmbeddingBatchSize, chunks.size() - startIndex));
        ResolvedRagEmbedding firstEmbedding = null;
        for (int index = startIndex; index < chunks.size() && batch.size() < indexEmbeddingBatchSize; index++) {
            Chunk chunk = chunks.get(index);
            if (completedChunkIndexes.contains(chunkIndex(chunk))) {
                break;
            }
            PendingChunk pending = pendingChunk(documentId, objectType, objectId, metadata, embeddingPort, chunk);
            if (firstEmbedding == null) {
                firstEmbedding = pending.resolvedEmbedding();
            } else if (!sameResolvedEmbedding(firstEmbedding, pending.resolvedEmbedding())) {
                break;
            }
            batch.add(pending);
        }
        return batch;
    }

    private PendingChunk pendingChunk(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            EmbeddingPort embeddingPort,
            Chunk chunk) {
        Map<String, Object> standardChunkMetadata = chunk.metadata().toMap();
        Map<String, Object> chunkMetadata = new HashMap<>(metadata);
        chunkMetadata.remove(ChunkMetadata.KEY_CHUNK_ORDER);
        mergeChunkMetadata(chunkMetadata, standardChunkMetadata);
        ResolvedRagEmbedding resolvedEmbedding = resolveEmbedding(embeddingPort, chunkMetadata, chunk);
        chunkMetadata.putAll(resolvedEmbedding.metadata());
        chunkMetadata.put(VectorRecord.KEY_DOCUMENT_ID, documentId);
        chunkMetadata.put(VectorRecord.KEY_CHUNK_ID, chunk.id());
        chunkMetadata.put("chunkLength", chunk.content().length());
        Object chunkOrder = standardChunkMetadata.get(ChunkMetadata.KEY_CHUNK_ORDER);
        if (chunkOrder != null) {
            chunkMetadata.put(VectorRecord.KEY_CHUNK_INDEX, chunkOrder);
        }
        chunkMetadata.put(VectorRecord.KEY_OBJECT_TYPE, objectType);
        chunkMetadata.put(VectorRecord.KEY_OBJECT_ID, objectId);
        return new PendingChunk(documentId, chunk, standardChunkMetadata, chunkMetadata, resolvedEmbedding);
    }

    private List<VectorRecord> embedPendingBatch(List<PendingChunk> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }
        ResolvedRagEmbedding resolvedEmbedding = batch.get(0).resolvedEmbedding();
        List<String> texts = batch.stream()
                .map(pending -> pending.chunk().content())
                .toList();
        EmbeddingResponse response = embedWithRetry(resolvedEmbedding, texts);
        if (response.vectors().size() != batch.size()) {
            throw new IllegalStateException(
                    "Embedding response size mismatch: requested=%d, actual=%d"
                            .formatted(batch.size(), response.vectors().size()));
        }
        List<VectorRecord> records = new ArrayList<>(batch.size());
        for (int index = 0; index < batch.size(); index++) {
            records.add(toVectorRecord(batch.get(index), response.vectors().get(index)));
        }
        return records;
    }

    private VectorRecord toVectorRecord(PendingChunk pending, EmbeddingVector vector) {
        Chunk chunk = pending.chunk();
        Map<String, Object> standardChunkMetadata = pending.standardChunkMetadata();
        Map<String, Object> chunkMetadata = pending.chunkMetadata();
        List<Double> embedding = List.copyOf(vector.values());
        return VectorRecord.builder()
                .id(chunk.id())
                .documentId(pending.documentId())
                .chunkId(chunk.id())
                .parentChunkId(text(chunkMetadata.get(VectorRecord.KEY_PARENT_CHUNK_ID)))
                .contentHash(contentHash(chunk.content()))
                .text(chunk.content())
                .embedding(embedding)
                .embeddingModel(embeddingModel(chunkMetadata))
                .embeddingDimension(embedding.size())
                .chunkType(text(standardChunkMetadata.get(VectorRecord.KEY_CHUNK_TYPE)))
                .headingPath(text(firstPresent(standardChunkMetadata, chunkMetadata, VectorRecord.KEY_HEADING_PATH)))
                .sourceRef(text(firstPresent(standardChunkMetadata, chunkMetadata,
                        VectorRecord.KEY_SOURCE_REF,
                        ChunkMetadata.KEY_SOURCE_REF,
                        ChunkMetadata.KEY_SOURCE_REFS)))
                .page(integer(firstPresent(standardChunkMetadata, chunkMetadata, VectorRecord.KEY_PAGE)))
                .slide(integer(firstPresent(standardChunkMetadata, chunkMetadata, VectorRecord.KEY_SLIDE)))
                .metadata(chunkMetadata)
                .build();
    }

    private boolean sameResolvedEmbedding(ResolvedRagEmbedding left, ResolvedRagEmbedding right) {
        return left.embeddingPort() == right.embeddingPort()
                && Objects.equals(left.profileId(), right.profileId())
                && Objects.equals(left.provider(), right.provider())
                && Objects.equals(left.model(), right.model())
                && Objects.equals(left.dimension(), right.dimension())
                && Objects.equals(left.modelId(), right.modelId())
                && Objects.equals(left.embeddingSpaceId(), right.embeddingSpaceId())
                && Objects.equals(left.deploymentId(), right.deploymentId())
                && Objects.equals(left.catalogId(), right.catalogId())
                && Objects.equals(left.contractVersion(), right.contractVersion())
                && left.inputType() == right.inputType();
    }

    private EmbeddingResponse embedWithRetry(ResolvedRagEmbedding resolvedEmbedding, List<String> contents) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= EMBEDDING_MAX_ATTEMPTS; attempt++) {
            try {
                return resolvedEmbedding.embeddingPort()
                        .embed(resolvedEmbedding.request(contents));
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt == EMBEDDING_MAX_ATTEMPTS) {
                    break;
                }
                sleepBeforeRetry();
            }
        }
        throw lastFailure;
    }

    private record PendingChunk(
            String documentId,
            Chunk chunk,
            Map<String, Object> standardChunkMetadata,
            Map<String, Object> chunkMetadata,
            ResolvedRagEmbedding resolvedEmbedding) {
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(EMBEDDING_RETRY_BACKOFF_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry embedding", ex);
        }
    }

    @Override
    public Optional<AttachmentRagIndexDiagnostics> latestDiagnostics() {
        return Optional.ofNullable(latestDiagnostics.get());
    }

    @Override
    public void clearDiagnostics() {
        latestDiagnostics.remove();
    }

    private boolean hasObjectScope(String objectType, String objectId) {
        return objectType != null && !objectType.isBlank()
                && objectId != null && !objectId.isBlank();
    }

    private ResolvedRagEmbedding resolveEmbedding(
            EmbeddingPort embeddingPort,
            Map<String, Object> metadata,
            Chunk chunk) {
        RagEmbeddingProfileResolver resolver = embeddingProfileResolverProvider == null
                ? null
                : embeddingProfileResolverProvider.getIfAvailable();
        RagEmbeddingSelection selection = new RagEmbeddingSelection(
                text(metadata.get(VectorRecord.KEY_EMBEDDING_PROFILE_ID)),
                text(metadata.get(VectorRecord.KEY_EMBEDDING_PROVIDER)),
                text(metadata.get(VectorRecord.KEY_EMBEDDING_MODEL)),
                null,
                embeddingInputType(chunk.metadata().chunkType()),
                text(metadata.get(VectorRecord.KEY_EMBEDDING_DEPLOYMENT_ID)));
        if (resolver != null) {
            return resolver.resolve(selection);
        }
        return new ResolvedRagEmbedding(
                embeddingPort,
                selection.profileId(),
                selection.provider(),
                selection.model(),
                null,
                selection.inputType());
    }

    private Set<Integer> completedChunkIndexes(
            VectorStorePort vectorStore,
            String objectType,
            String objectId,
            Map<String, Object> expectedMetadata) {
        if (!hasEmbeddingSelection(expectedMetadata)) {
            return Set.of();
        }
        try {
            Set<Integer> completed = new HashSet<>();
            for (VectorSearchResult result : vectorStore.listByObject(objectType, objectId, Integer.MAX_VALUE)) {
                Map<String, Object> metadata = result.document().metadata();
                if (!sameEmbeddingSelection(expectedMetadata, metadata)) {
                    continue;
                }
                Integer chunkIndex = integer(metadata.get(VectorRecord.KEY_CHUNK_INDEX));
                if (chunkIndex != null) {
                    completed.add(chunkIndex);
                }
            }
            return completed;
        } catch (UnsupportedOperationException ex) {
            return Set.of();
        }
    }

    private boolean hasEmbeddingSelection(Map<String, Object> metadata) {
        return text(metadata.get(VectorRecord.KEY_EMBEDDING_PROFILE_ID)) != null
                || text(metadata.get(VectorRecord.KEY_EMBEDDING_PROVIDER)) != null
                || text(metadata.get(VectorRecord.KEY_EMBEDDING_MODEL)) != null;
    }

    private boolean sameEmbeddingSelection(Map<String, Object> expected, Map<String, Object> actual) {
        String expectedSpace = firstText(expected,
                VectorRecord.KEY_EMBEDDING_SPACE_ID_V2, VectorRecord.KEY_EMBEDDING_SPACE_ID);
        String actualSpace = firstText(actual,
                VectorRecord.KEY_EMBEDDING_SPACE_ID_V2, VectorRecord.KEY_EMBEDDING_SPACE_ID);
        if (expectedSpace != null || actualSpace != null) {
            return Objects.equals(expectedSpace, actualSpace);
        }
        return Objects.equals(
                text(expected.get(VectorRecord.KEY_EMBEDDING_PROFILE_ID)),
                text(actual.get(VectorRecord.KEY_EMBEDDING_PROFILE_ID)))
                && Objects.equals(
                        text(expected.get(VectorRecord.KEY_EMBEDDING_PROVIDER)),
                        text(actual.get(VectorRecord.KEY_EMBEDDING_PROVIDER)))
                && Objects.equals(
                        text(expected.get(VectorRecord.KEY_EMBEDDING_MODEL)),
                        text(actual.get(VectorRecord.KEY_EMBEDDING_MODEL)));
    }

    private String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            String value = text(metadata.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private EmbeddingInputType embeddingInputType(ChunkType chunkType) {
        if (chunkType == ChunkType.TABLE) {
            return EmbeddingInputType.TABLE_TEXT;
        }
        if (chunkType == ChunkType.IMAGE_CAPTION) {
            return EmbeddingInputType.IMAGE_CAPTION;
        }
        if (chunkType == ChunkType.OCR) {
            return EmbeddingInputType.OCR_TEXT;
        }
        return EmbeddingInputType.TEXT;
    }

    private String fallbackReason(
            TextractNormalizedDocumentAdapter adapter,
            ChunkingOrchestrator chunkingOrchestrator,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStore,
            String objectType,
            String objectId) {
        if (adapter == null) {
            return "missing_normalized_document_adapter";
        }
        if (chunkingOrchestrator == null) {
            return "missing_chunking_orchestrator";
        }
        if (embeddingPort == null) {
            return "missing_embedding_port";
        }
        if (vectorStore == null) {
            return "missing_vector_store";
        }
        if (!hasObjectScope(objectType, objectId)) {
            return "missing_object_scope";
        }
        return null;
    }

    private String stagedFallbackReason(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStore,
            String objectType,
            String objectId) {
        if (embeddingPort == null) {
            return "missing_embedding_port";
        }
        if (vectorStore == null) {
            return "missing_vector_store";
        }
        if (!hasObjectScope(objectType, objectId)) {
            return "missing_object_scope";
        }
        return null;
    }

    private NormalizedDocument enrichMetadata(
            String documentId,
            NormalizedDocument document,
            Map<String, Object> metadata) {
        Map<String, Object> mergedMetadata = new HashMap<>(document.metadata());
        mergedMetadata.putAll(metadata);
        return NormalizedDocument.builder(documentId)
                .plainText(document.plainText())
                .sourceFormat(document.sourceFormat())
                .filename(document.filename())
                .blocks(document.blocks())
                .metadata(mergedMetadata)
                .build();
    }

    private void mergeChunkMetadata(Map<String, Object> metadata, Map<String, Object> chunkMetadata) {
        chunkMetadata.forEach((key, value) -> {
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                metadata.putIfAbsent(key, value);
            }
        });
    }

    private Object firstPresent(Map<String, Object> preferred, Map<String, Object> fallback, String key) {
        Object value = value(preferred, key);
        return value == null ? value(fallback, key) : value;
    }

    private Object firstPresent(Map<String, Object> preferred, Map<String, Object> fallback, String... keys) {
        for (String key : keys) {
            Object value = firstPresent(preferred, fallback, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = value(metadata, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object value(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null || (value instanceof String textValue && textValue.isBlank())) {
            return null;
        }
        return value;
    }

    private String embeddingModel(Map<String, Object> metadata) {
        String embeddingModel = text(metadata.get(VectorRecord.KEY_EMBEDDING_MODEL));
        return embeddingModel == null ? "unknown" : embeddingModel;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            return join(iterable);
        }
        String text = Objects.toString(value, null);
        return text == null || text.isBlank() ? null : text;
    }

    private String join(Iterable<?> values) {
        String joined = java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + " > " + right)
                .orElse(null);
        return joined == null || joined.isBlank() ? null : joined;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String contentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private Integer chunkIndex(Chunk chunk) {
        return integer(chunk.metadata().toMap().get(ChunkMetadata.KEY_CHUNK_ORDER));
    }

    private int chunkIndex(VectorRecord record, int fallback) {
        Integer chunkIndex = integer(record.metadata().get(VectorRecord.KEY_CHUNK_INDEX));
        return chunkIndex == null ? fallback : chunkIndex;
    }
}
