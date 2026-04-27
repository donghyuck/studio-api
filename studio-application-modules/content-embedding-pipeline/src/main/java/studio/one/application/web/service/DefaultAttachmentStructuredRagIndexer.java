package studio.one.application.web.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexJobStep;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagEmbeddingSelection;
import studio.one.platform.ai.core.rag.RagChunkingOptions;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.ResolvedRagEmbedding;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.textract.model.ParsedFile;
import studio.one.platform.textract.service.FileContentExtractionService;

@Component
@ConditionalOnMissingBean(AttachmentStructuredRagIndexer.class)
@ConditionalOnClass(name = {
        "studio.one.platform.chunking.core.ChunkingOrchestrator",
        "studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter",
        "studio.one.platform.textract.model.ParsedFile"
})
public class DefaultAttachmentStructuredRagIndexer implements AttachmentStructuredRagIndexer {

    private final ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider;
    private final ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;
    private final ObjectProvider<RagEmbeddingProfileResolver> embeddingProfileResolverProvider;
    private final ObjectProvider<VectorStorePort> vectorStoreProvider;
    private final ThreadLocal<AttachmentRagIndexDiagnostics> latestDiagnostics = new ThreadLocal<>();

    public DefaultAttachmentStructuredRagIndexer(
            ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<VectorStorePort> vectorStoreProvider) {
        this(normalizedDocumentAdapterProvider, chunkingOrchestratorProvider, embeddingPortProvider, null, vectorStoreProvider);
    }

    public DefaultAttachmentStructuredRagIndexer(
            ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider,
            ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<RagEmbeddingProfileResolver> embeddingProfileResolverProvider,
            ObjectProvider<VectorStorePort> vectorStoreProvider) {
        this.normalizedDocumentAdapterProvider = normalizedDocumentAdapterProvider;
        this.chunkingOrchestratorProvider = chunkingOrchestratorProvider;
        this.embeddingPortProvider = embeddingPortProvider;
        this.embeddingProfileResolverProvider = embeddingProfileResolverProvider;
        this.vectorStoreProvider = vectorStoreProvider;
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
        return index(attachment, documentId, objectType, objectId, metadata, extractor, inputStream,
                listener, RagChunkingOptions.empty());
    }

    @Override
    public boolean index(Attachment attachment,
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            FileContentExtractionService extractor,
            InputStream inputStream,
            RagIndexProgressListener listener,
            RagChunkingOptions chunkingOptions) throws IOException {
        latestDiagnostics.remove();
        RagIndexProgressListener progress = listener == null ? RagIndexProgressListener.noop() : listener;
        TextractNormalizedDocumentAdapter adapter = normalizedDocumentAdapterProvider.getIfAvailable();
        ChunkingOrchestrator chunkingOrchestrator = chunkingOrchestratorProvider.getIfAvailable();
        EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
        VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
        String fallbackReason = fallbackReason(adapter, chunkingOrchestrator, embeddingPort, vectorStore, objectType, objectId);
        if (fallbackReason != null) {
            latestDiagnostics.set(AttachmentRagIndexDiagnostics.fallback(fallbackReason));
            return false;
        }
        if (requiresPlainTextChunking(chunkingOptions)) {
            latestDiagnostics.set(AttachmentRagIndexDiagnostics.fallback("plain_text_chunking_strategy"));
            return false;
        }

        progress.onStep(RagIndexJobStep.EXTRACTING);
        ParsedFile parsedFile = extractor.parseStructured(attachment.getContentType(), attachment.getName(), inputStream);
        NormalizedDocument normalizedDocument = adapter.adapt(documentId, parsedFile);
        NormalizedDocument document = enrichMetadata(documentId, normalizedDocument, metadata);
        progress.onStep(RagIndexJobStep.CHUNKING);
        List<Chunk> chunks = chunk(document, chunkingOrchestrator, chunkingOptions);
        progress.onChunkCount(chunks.size());
        if (chunks.isEmpty()) {
            latestDiagnostics.set(AttachmentRagIndexDiagnostics.structured(parsedFile.blocks().size(), 0, 0));
            progress.onStep(RagIndexJobStep.INDEXING);
            vectorStore.replaceRecordsByObject(objectType, objectId, List.of());
            progress.onIndexedCount(0);
            return true;
        }

        progress.onStep(RagIndexJobStep.EMBEDDING);
        List<VectorRecord> records = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
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
            EmbeddingVector vector = resolvedEmbedding.embeddingPort()
                    .embed(resolvedEmbedding.request(List.of(chunk.content())))
                    .vectors()
                    .get(0);
            progress.onEmbeddedCount(i + 1);
            List<Double> embedding = List.copyOf(vector.values());
            records.add(VectorRecord.builder()
                    .id(chunk.id())
                    .documentId(documentId)
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
                    .build());
        }
        progress.onStep(RagIndexJobStep.INDEXING);
        vectorStore.replaceRecordsByObject(objectType, objectId, records);
        progress.onIndexedCount(records.size());
        latestDiagnostics.set(AttachmentRagIndexDiagnostics.structured(
                parsedFile.blocks().size(),
                chunks.size(),
                records.size()));
        return true;
    }

    private List<Chunk> chunk(
            NormalizedDocument document,
            ChunkingOrchestrator chunkingOrchestrator,
            RagChunkingOptions options) {
        if (options == null || options.isEmpty()) {
            return chunkingOrchestrator.chunk(document);
        }
        ChunkingContext.Builder builder = document.toContextBuilder()
                .strategy(ChunkingStrategyType.STRUCTURE_BASED)
                .useConfiguredMaxSize()
                .useConfiguredOverlap();
        if (options.strategy() != null) {
            builder.strategy(ChunkingStrategyType.from(options.strategy()));
        }
        if (options.maxSize() != null) {
            builder.maxSize(options.maxSize());
        }
        if (options.overlap() != null) {
            builder.overlap(options.overlap());
        }
        if (options.unit() != null) {
            builder.unit(ChunkUnit.from(options.unit()));
        }
        return chunkingOrchestrator.chunk(document, builder.build());
    }

    private boolean requiresPlainTextChunking(RagChunkingOptions options) {
        return options != null
                && options.strategy() != null
                && !"structure-based".equals(options.strategy());
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
                embeddingInputType(chunk.metadata().chunkType()));
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
}
