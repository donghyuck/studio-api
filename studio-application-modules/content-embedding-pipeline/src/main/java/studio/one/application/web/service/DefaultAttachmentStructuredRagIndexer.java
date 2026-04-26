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

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.textract.model.ParsedFile;
import studio.one.platform.textract.service.FileContentExtractionService;

@Component
@RequiredArgsConstructor
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
    private final ObjectProvider<VectorStorePort> vectorStoreProvider;
    private final ThreadLocal<AttachmentRagIndexDiagnostics> latestDiagnostics = new ThreadLocal<>();

    @Override
    public boolean index(Attachment attachment,
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            FileContentExtractionService extractor,
            InputStream inputStream) throws IOException {
        latestDiagnostics.remove();
        TextractNormalizedDocumentAdapter adapter = normalizedDocumentAdapterProvider.getIfAvailable();
        ChunkingOrchestrator chunkingOrchestrator = chunkingOrchestratorProvider.getIfAvailable();
        EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
        VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
        String fallbackReason = fallbackReason(adapter, chunkingOrchestrator, embeddingPort, vectorStore, objectType, objectId);
        if (fallbackReason != null) {
            latestDiagnostics.set(AttachmentRagIndexDiagnostics.fallback(fallbackReason));
            return false;
        }

        ParsedFile parsedFile = extractor.parseStructured(attachment.getContentType(), attachment.getName(), inputStream);
        NormalizedDocument normalizedDocument = adapter.adapt(documentId, parsedFile);
        NormalizedDocument document = enrichMetadata(documentId, normalizedDocument, metadata);
        List<Chunk> chunks = chunkingOrchestrator.chunk(document);
        if (chunks.isEmpty()) {
            latestDiagnostics.set(AttachmentRagIndexDiagnostics.structured(parsedFile.blocks().size(), 0, 0));
            vectorStore.replaceRecordsByObject(objectType, objectId, List.of());
            return true;
        }

        EmbeddingResponse response = embeddingPort.embed(new EmbeddingRequest(
                chunks.stream().map(Chunk::content).toList()));
        List<EmbeddingVector> vectors = response.vectors();
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding response size does not match chunk count");
        }

        List<VectorRecord> records = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            Map<String, Object> standardChunkMetadata = chunk.metadata().toMap();
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            chunkMetadata.remove(ChunkMetadata.KEY_CHUNK_ORDER);
            mergeChunkMetadata(chunkMetadata, standardChunkMetadata);
            chunkMetadata.put(VectorRecord.KEY_DOCUMENT_ID, documentId);
            chunkMetadata.put(VectorRecord.KEY_CHUNK_ID, chunk.id());
            chunkMetadata.put("chunkLength", chunk.content().length());
            Object chunkOrder = standardChunkMetadata.get(ChunkMetadata.KEY_CHUNK_ORDER);
            if (chunkOrder != null) {
                chunkMetadata.put(VectorRecord.KEY_CHUNK_INDEX, chunkOrder);
            }
            chunkMetadata.put(VectorRecord.KEY_OBJECT_TYPE, objectType);
            chunkMetadata.put(VectorRecord.KEY_OBJECT_ID, objectId);
            List<Double> embedding = List.copyOf(vectors.get(i).values());
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
        vectorStore.replaceRecordsByObject(objectType, objectId, records);
        latestDiagnostics.set(AttachmentRagIndexDiagnostics.structured(
                parsedFile.blocks().size(),
                chunks.size(),
                vectors.size()));
        return true;
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
