package studio.one.application.webknowledge.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.application.webknowledge.infrastructure.web.WebPageFetchException;
import studio.one.application.webknowledge.infrastructure.web.WebUrlPolicy;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.service.pipeline.RagChunkStage;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagEmbeddingSelection;
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceExecutor;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.pipeline.ResolvedRagEmbedding;
import studio.one.platform.chunking.artifact.ChunkSet;
import studio.one.platform.chunking.artifact.ChunkSetItem;
import studio.one.platform.chunking.artifact.ChunkSetQualityStatus;
import studio.one.platform.chunking.artifact.ChunkSetStatus;
import studio.one.platform.chunking.artifact.ChunkSetStore;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.textract.infrastructure.extractor.impl.HtmlFileParser;
import tools.jackson.databind.ObjectMapper;

public class WebKnowledgeRagIndexJobSourceExecutor implements RagIndexJobSourceExecutor {

    public static final String SOURCE_TYPE = "web_source";

    private final WebKnowledgeSourceJpaRepository sources;
    private final WebKnowledgeRevisionJpaRepository revisions;
    private final WebKnowledgeStatePersistence statePersistence;
    private final WebPageFetchPort fetchPort;
    private final WebPageMetadataExtractor metadataExtractor;
    private final HtmlFileParser htmlParser;
    private final TextractNormalizedDocumentAdapter normalizedDocumentAdapter;
    private final ChunkingOrchestrator chunking;
    private final RagChunkStageStore stageStore;
    private final ChunkSetStore chunkSetStore;
    private final RagPipelineService ragPipeline;
    private final RagEmbeddingProfileResolver embeddingResolver;
    private final WebPageFetchPolicy fetchPolicy;
    private final WebKnowledgeContentSanitizer contentSanitizer;
    private final ObjectMapper objectMapper;

    public WebKnowledgeRagIndexJobSourceExecutor(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            WebKnowledgeStatePersistence statePersistence,
            WebPageFetchPort fetchPort,
            WebPageMetadataExtractor metadataExtractor,
            HtmlFileParser htmlParser,
            TextractNormalizedDocumentAdapter normalizedDocumentAdapter,
            ChunkingOrchestrator chunking,
            RagChunkStageStore stageStore,
            ChunkSetStore chunkSetStore,
            RagPipelineService ragPipeline,
            RagEmbeddingProfileResolver embeddingResolver,
            WebPageFetchPolicy fetchPolicy,
            WebKnowledgeContentSanitizer contentSanitizer,
            ObjectMapper objectMapper) {
        this.sources = sources;
        this.revisions = revisions;
        this.statePersistence = statePersistence;
        this.fetchPort = fetchPort;
        this.metadataExtractor = metadataExtractor;
        this.htmlParser = htmlParser;
        this.normalizedDocumentAdapter = normalizedDocumentAdapter;
        this.chunking = chunking;
        this.stageStore = stageStore;
        this.chunkSetStore = chunkSetStore;
        this.ragPipeline = ragPipeline;
        this.embeddingResolver = embeddingResolver;
        this.fetchPolicy = fetchPolicy;
        this.contentSanitizer = contentSanitizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
        return request != null
                && SOURCE_TYPE.equalsIgnoreCase(request.sourceType())
                && SOURCE_TYPE.equalsIgnoreCase(request.objectType());
    }

    @Override
    public void execute(
            RagIndexJob job,
            RagIndexJobCreateRequest request,
            RagIndexJobSourceRequest sourceRequest,
            RagIndexProgressListener listener) {
        WebKnowledgeSourceEntity source = sources.findById(request.objectId())
                .filter(candidate -> !candidate.archived())
                .orElseThrow(() -> new NoSuchElementException("WEB_SOURCE_NOT_FOUND"));
        WebKnowledgeRevisionEntity revision = revisions
                .findByRevisionIdAndSourceId(request.documentId(), source.sourceId())
                .orElseThrow(() -> new NoSuchElementException("WEB_SOURCE_REVISION_NOT_FOUND"));
        Instant now = Instant.now();
        try {
            String deploymentId = sourceRequest == null
                    ? source.embeddingDeploymentId()
                    : sourceRequest.embeddingDeploymentId();
            if (!source.embeddingDeploymentId().equals(deploymentId)) {
                throw new IllegalStateException("BLOCKED_MODEL_CONFIGURATION");
            }
            ResolvedRagEmbedding embedding;
            try {
                embedding = embeddingResolver.resolve(new RagEmbeddingSelection(
                        null, null, null, null, EmbeddingInputType.TEXT, deploymentId));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("BLOCKED_MODEL_CONFIGURATION", ex);
            }
            if (source.embeddingSpaceId() != null
                    && !source.embeddingSpaceId().equals(embedding.embeddingSpaceId())) {
                throw new IllegalStateException("BLOCKED_MODEL_CONFIGURATION");
            }

            revision.status("FETCHING", now);
            source.status("FETCHING", now);
            WebKnowledgeStatePersistence.PersistedState persisted = save(source, revision);
            source = persisted.source();
            revision = persisted.revision();

            WebKnowledgeRevisionEntity current = currentRevision(source);
            WebPageFetchPort.ConditionalRequest conditional = current == null
                    ? WebPageFetchPort.ConditionalRequest.none()
                    : new WebPageFetchPort.ConditionalRequest(current.etag(), current.lastModified());
            WebPageFetchPort.FetchResult fetched = fetchPort.fetch(URI.create(source.normalizedUrl()), conditional);
            now = Instant.now();
            revision.fetched(
                    fetched.contentType(),
                    fetched.body().length,
                    bounded(fetched.etag(), 500),
                    bounded(fetched.lastModified(), 500),
                    fetched.retrievedAt(),
                    now);
            if (fetched.notModified()) {
                unchanged(source, revision, now);
                return;
            }

            revision.status("NORMALIZING", now);
            source.status("NORMALIZING", now);
            persisted = save(source, revision);
            source = persisted.source();
            revision = persisted.revision();
            WebPageMetadataExtractor.Metadata pageMetadata = sanitizeCanonicalMetadata(
                    metadataExtractor.extract(fetched.body(), fetched.finalUri()),
                    fetched.finalUri());
            var parsed = htmlParser.parseStructured(
                    fetched.body(), fetched.contentType(), source.host() + ".html");
            NormalizedDocument adapted = contentSanitizer.sanitize(
                    normalizedDocumentAdapter.adapt(revision.revisionId(), parsed));
            pageMetadata = sanitizePiiMetadata(pageMetadata);
            if (adapted.chunkableText().isBlank()) {
                throw new IllegalStateException("NO_EXTRACTABLE_CONTENT");
            }
            if (adapted.chunkableText().length() > fetchPolicy.maxNormalizedChars()) {
                throw new IllegalStateException("NORMALIZED_CONTENT_TOO_LARGE");
            }

            String contentHash = sha256(adapted.chunkableText());
            if (current != null && contentHash.equals(current.contentHash())) {
                unchanged(source, revision, Instant.now());
                return;
            }
            Map<String, Object> metadata = metadata(source, revision, fetched, pageMetadata, contentHash);
            NormalizedDocument document = new NormalizedDocument(
                    adapted.sourceDocumentId(),
                    adapted.plainText(),
                    adapted.sourceFormat(),
                    adapted.filename(),
                    adapted.blocks(),
                    merge(adapted.metadata(), metadata));
            String snapshot = objectMapper.writeValueAsString(document);
            String metadataJson = objectMapper.writeValueAsString(pageMetadata.values());
            String preview = bounded(document.chunkableText(), 500);
            revision.normalized(
                    pageMetadata.title(),
                    pageMetadata.publisher(),
                    pageMetadata.language(),
                    pageMetadata.publishedAt(),
                    pageMetadata.modifiedAt(),
                    contentHash,
                    snapshot,
                    preview,
                    metadataJson,
                    Instant.now());

            List<Chunk> chunks = chunking.chunk(document);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("NO_CHUNKS");
            }
            String chunkSetId = "wcset-" + UUID.randomUUID();
            String strategyHash = sha256("web-source:structure-based:v1");
            List<RagChunkStage> stages = new ArrayList<>(chunks.size());
            List<ChunkSetItem> items = new ArrayList<>(chunks.size());
            for (int index = 0; index < chunks.size(); index++) {
                Chunk chunk = chunks.get(index);
                Map<String, Object> chunkMetadata = new LinkedHashMap<>(metadata);
                chunkMetadata.putAll(chunk.metadata().toMap());
                chunkMetadata.put("chunkSetId", chunkSetId);
                chunkMetadata.put("ragRechunkApplied", false);
                stages.add(new RagChunkStage(
                        SOURCE_TYPE,
                        source.sourceId(),
                        revision.revisionId(),
                        index,
                        chunk.id(),
                        chunk.content(),
                        chunkMetadata,
                        null));
                items.add(new ChunkSetItem(
                        index,
                        chunk.id(),
                        chunk.content(),
                        sha256(chunk.content()),
                        chunkMetadata));
            }
            stageStore.replace(SOURCE_TYPE, source.sourceId(), revision.revisionId(), stages);
            Instant artifactNow = Instant.now();
            chunkSetStore.save(new ChunkSet(
                    chunkSetId,
                    SOURCE_TYPE,
                    source.sourceId(),
                    revision.revisionId(),
                    revision.revisionId(),
                    contentHash,
                    "structure-based",
                    strategyHash,
                    null,
                    null,
                    null,
                    ChunkSetStatus.READY,
                    ChunkSetQualityStatus.VALID,
                    List.of(),
                    metadata,
                    items,
                    artifactNow,
                    artifactNow));

            revision.status("INDEXING", Instant.now());
            source.status("INDEXING", Instant.now());
            persisted = save(source, revision);
            source = persisted.source();
            revision = persisted.revision();
            ragPipeline.index(new RagIndexRequest(
                    revision.revisionId(),
                    document.chunkableText(),
                    merge(metadata, Map.of(
                            "chunkSetId", chunkSetId,
                            "requirePreparedChunks", true,
                            "ragRechunkApplied", false)),
                    List.of(),
                    false,
                    null,
                    null,
                    null,
                    null,
                    deploymentId), listener);

            revision.status("COMPLETED", Instant.now());
            source.complete(
                    revision.revisionId(),
                    pageMetadata.canonicalUri().toString(),
                    embedding.embeddingSpaceId(),
                    Instant.now());
            save(source, revision);
        } catch (Exception ex) {
            String code = errorCode(ex);
            revision.fail(code, Instant.now());
            source.status("FAILED", Instant.now());
            save(source, revision);
            throw new IllegalStateException("Web source RAG index job failed: " + code, ex);
        }
    }

    private WebKnowledgeRevisionEntity currentRevision(WebKnowledgeSourceEntity source) {
        return source.currentRevisionId() == null
                ? null
                : revisions.findByRevisionIdAndSourceId(source.currentRevisionId(), source.sourceId()).orElse(null);
    }

    private void unchanged(
            WebKnowledgeSourceEntity source,
            WebKnowledgeRevisionEntity revision,
            Instant now) {
        revision.status("UNCHANGED", now);
        source.unchanged(now);
        save(source, revision);
    }

    private WebKnowledgeStatePersistence.PersistedState save(
            WebKnowledgeSourceEntity source,
            WebKnowledgeRevisionEntity revision) {
        return statePersistence.save(source, revision);
    }

    private Map<String, Object> metadata(
            WebKnowledgeSourceEntity source,
            WebKnowledgeRevisionEntity revision,
            WebPageFetchPort.FetchResult fetched,
            WebPageMetadataExtractor.Metadata pageMetadata,
            String contentHash) {
        Map<String, Object> metadata = new HashMap<>(pageMetadata.values());
        metadata.put("objectType", SOURCE_TYPE);
        metadata.put("objectId", source.sourceId());
        metadata.put("sourceType", "WEB_PAGE");
        metadata.put("sourceRevisionId", revision.revisionId());
        metadata.put("evidenceOrigin", "INDEXED_WEB");
        metadata.put("contentHash", contentHash);
        metadata.put("canonicalUrl", pageMetadata.canonicalUri().toString());
        metadata.put("retrievedAt", fetched.retrievedAt().toString());
        if (source.displayName() != null) {
            metadata.put("displayName", source.displayName());
        }
        return Map.copyOf(metadata);
    }

    private WebPageMetadataExtractor.Metadata sanitizeCanonicalMetadata(
            WebPageMetadataExtractor.Metadata metadata,
            URI fetchedUri) {
        URI canonical = fetchedUri;
        try {
            URI candidate = WebUrlPolicy.normalize(metadata.canonicalUri().toString());
            WebUrlPolicy.assertPublicHost(candidate);
            canonical = candidate;
        } catch (RuntimeException ignored) {
            // Untrusted page metadata cannot redirect public references to a blocked host.
        }
        Map<String, Object> values = new LinkedHashMap<>(metadata.values());
        values.put("canonicalUrl", canonical.toString());
        return new WebPageMetadataExtractor.Metadata(
                metadata.title(),
                metadata.publisher(),
                metadata.language(),
                metadata.publishedAt(),
                metadata.modifiedAt(),
                canonical,
                Map.copyOf(values));
    }

    private WebPageMetadataExtractor.Metadata sanitizePiiMetadata(
            WebPageMetadataExtractor.Metadata metadata) {
        Map<String, Object> values = new LinkedHashMap<>(
                contentSanitizer.sanitizeMetadata(metadata.values()));
        String title = contentSanitizer.sanitizeText(metadata.title());
        String publisher = contentSanitizer.sanitizeText(metadata.publisher());
        if (title != null) {
            values.put("title", title);
        }
        if (publisher != null) {
            values.put("publisher", publisher);
        }
        return new WebPageMetadataExtractor.Metadata(
                title,
                publisher,
                metadata.language(),
                metadata.publishedAt(),
                metadata.modifiedAt(),
                metadata.canonicalUri(),
                Map.copyOf(values));
    }

    private static Map<String, Object> merge(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            merged.putAll(right);
        }
        return Map.copyOf(merged);
    }

    private static String errorCode(Exception ex) {
        if (ex instanceof WebPageFetchException fetchException) {
            return fetchException.errorCode();
        }
        String message = ex.getMessage();
        if ("NO_EXTRACTABLE_CONTENT".equals(message)
                || "NORMALIZED_CONTENT_TOO_LARGE".equals(message)
                || "NO_CHUNKS".equals(message)
                || "BLOCKED_MODEL_CONFIGURATION".equals(message)) {
            return message;
        }
        return "WEB_SOURCE_PROCESSING_FAILED";
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
