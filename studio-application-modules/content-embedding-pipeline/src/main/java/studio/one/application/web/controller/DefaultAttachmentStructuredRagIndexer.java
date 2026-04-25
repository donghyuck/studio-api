package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.chunking.core.Chunk;
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
class DefaultAttachmentStructuredRagIndexer implements AttachmentStructuredRagIndexer {

    private final ObjectProvider<TextractNormalizedDocumentAdapter> normalizedDocumentAdapterProvider;
    private final ObjectProvider<ChunkingOrchestrator> chunkingOrchestratorProvider;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;
    private final ObjectProvider<VectorStorePort> vectorStoreProvider;

    @Override
    public boolean index(Attachment attachment,
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            FileContentExtractionService extractor,
            InputStream inputStream) throws IOException {
        TextractNormalizedDocumentAdapter adapter = normalizedDocumentAdapterProvider.getIfAvailable();
        ChunkingOrchestrator chunkingOrchestrator = chunkingOrchestratorProvider.getIfAvailable();
        EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
        VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
        if (adapter == null || chunkingOrchestrator == null || embeddingPort == null || vectorStore == null) {
            return false;
        }

        ParsedFile parsedFile = extractor.parseStructured(attachment.getContentType(), attachment.getName(), inputStream);
        NormalizedDocument normalizedDocument = adapter.adapt(documentId, parsedFile);
        NormalizedDocument document = enrichMetadata(documentId, normalizedDocument, metadata);
        List<Chunk> chunks = chunkingOrchestrator.chunk(document);
        if (chunks.isEmpty()) {
            vectorStore.replaceByObject(objectType, objectId, List.of());
            return true;
        }

        EmbeddingResponse response = embeddingPort.embed(new EmbeddingRequest(
                chunks.stream().map(Chunk::content).toList()));
        List<EmbeddingVector> vectors = response.vectors();
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding response size does not match chunk count");
        }

        List<VectorDocument> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            mergeChunkMetadata(chunkMetadata, chunk.metadata().toMap());
            chunkMetadata.put("documentId", documentId);
            chunkMetadata.put("chunkId", chunk.id());
            chunkMetadata.put("chunkOrder", i);
            chunkMetadata.put("chunkLength", chunk.content().length());
            documents.add(new VectorDocument(
                    chunk.id(),
                    chunk.content(),
                    chunkMetadata,
                    List.copyOf(vectors.get(i).values())));
        }
        vectorStore.replaceByObject(objectType, objectId, documents);
        return true;
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
}
