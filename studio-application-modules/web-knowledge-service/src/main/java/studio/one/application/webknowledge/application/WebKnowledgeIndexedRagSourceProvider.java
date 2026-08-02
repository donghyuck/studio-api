package studio.one.application.webknowledge.application;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCorpusPageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCorpusRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceCapabilities;
import studio.one.platform.ai.core.rag.indexed.ResolvedIndexedRagSource;

public class WebKnowledgeIndexedRagSourceProvider implements IndexedRagSourceProvider {

    private final WebKnowledgeSourceJpaRepository sources;
    private final WebKnowledgeRevisionJpaRepository revisions;
    private final WebKnowledgeCorpusRevisionJpaRepository corpusRevisions;
    private final WebKnowledgeCorpusPageJpaRepository corpusPages;
    private final WebKnowledgePageRevisionJpaRepository pageRevisions;
    private final int maxSelectedSources;
    private final IndexedRagSourceCapabilities capabilities;

    public WebKnowledgeIndexedRagSourceProvider(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            int maxSelectedSources) {
        this(sources, revisions, null, null, null, maxSelectedSources,
                IndexedRagSourceCapabilities.singlePage(maxSelectedSources));
    }

    public WebKnowledgeIndexedRagSourceProvider(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            WebKnowledgeCorpusRevisionJpaRepository corpusRevisions,
            WebKnowledgeCorpusPageJpaRepository corpusPages,
            WebKnowledgePageRevisionJpaRepository pageRevisions,
            int maxSelectedSources) {
        this(
                sources,
                revisions,
                corpusRevisions,
                corpusPages,
                pageRevisions,
                maxSelectedSources,
                IndexedRagSourceCapabilities.singlePage(maxSelectedSources));
    }

    public WebKnowledgeIndexedRagSourceProvider(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            WebKnowledgeCorpusRevisionJpaRepository corpusRevisions,
            WebKnowledgeCorpusPageJpaRepository corpusPages,
            WebKnowledgePageRevisionJpaRepository pageRevisions,
            int maxSelectedSources,
            IndexedRagSourceCapabilities capabilities) {
        this.sources = sources;
        this.revisions = revisions;
        this.corpusRevisions = corpusRevisions;
        this.corpusPages = corpusPages;
        this.pageRevisions = pageRevisions;
        this.maxSelectedSources = Math.max(1, Math.min(10, maxSelectedSources));
        this.capabilities = capabilities == null
                ? IndexedRagSourceCapabilities.singlePage(this.maxSelectedSources)
                : capabilities;
    }

    @Override
    public boolean supports(String sourceType) {
        return WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE.equalsIgnoreCase(sourceType);
    }

    @Override
    public int maxSelectedSources() {
        return maxSelectedSources;
    }

    @Override
    public IndexedRagSourceCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public Optional<ResolvedIndexedRagSource> resolve(String sourceId, String revisionId) {
        return sources.findById(sourceId)
                .filter(source -> !source.archived())
                .flatMap(source -> {
                    if (WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())) {
                        return resolveCorpus(source, revisionId);
                    }
                    String selectedRevision = normalize(revisionId);
                    if (selectedRevision == null) {
                        selectedRevision = source.currentRevisionId();
                    }
                    if (selectedRevision == null) {
                        return Optional.empty();
                    }
                    String resolvedRevision = selectedRevision;
                    return revisions.findByRevisionIdAndSourceId(resolvedRevision, source.sourceId())
                            .filter(revision -> "COMPLETED".equals(revision.status()))
                            .map(revision -> {
                                Map<String, Object> metadata = new LinkedHashMap<>();
                                put(metadata, "title", revision.title());
                                put(metadata, "publisher", revision.publisher());
                                put(metadata, "canonicalUrl", source.canonicalUrl());
                                put(metadata, "publishedAt", revision.publishedAt());
                                put(metadata, "modifiedAt", revision.modifiedAt());
                                put(metadata, "retrievedAt", revision.retrievedAt());
                                metadata.put("workspaceId", source.workspaceId());
                                metadata.put("evidenceOrigin", "INDEXED_WEB");
                                metadata.put("sourceType", "INDEXED_WEB");
                                return new ResolvedIndexedRagSource(
                                        WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE,
                                        source.sourceId(),
                                        revision.revisionId(),
                                        WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE,
                                        source.sourceId(),
                                        revision.contentHash(),
                                        source.embeddingDeploymentId(),
                                        source.embeddingSpaceId(),
                                        metadata);
                            });
                });
    }

    private Optional<ResolvedIndexedRagSource> resolveCorpus(
            studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity source,
            String requestedRevision) {
        if (corpusRevisions == null || corpusPages == null || pageRevisions == null) {
            return Optional.empty();
        }
        String selectedRevision = normalize(requestedRevision);
        if (selectedRevision == null) {
            selectedRevision = source.currentCorpusRevisionId();
        }
        if (selectedRevision == null) {
            return Optional.empty();
        }
        return corpusRevisions
                .findByCorpusRevisionIdAndWorkspaceIdAndSourceIdAndStatus(
                        selectedRevision, source.workspaceId(), source.sourceId(), "COMPLETED")
                .map(corpus -> {
                    var manifest = corpusPages.findByCorpusRevisionIdOrderByPageOrder(corpus.corpusRevisionId());
                    Set<String> partitions = manifest.stream()
                            .map(item -> item.pageRevisionId())
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                    var seedRevision = manifest.stream()
                            .findFirst()
                            .flatMap(item -> pageRevisions
                                    .findByPageRevisionIdAndWorkspaceIdAndSourceId(
                                            item.pageRevisionId(), source.workspaceId(), source.sourceId()))
                            .orElse(null);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    put(metadata, "title", source.displayName() == null && seedRevision != null
                            ? seedRevision.title()
                            : source.displayName());
                    put(metadata, "canonicalUrl", source.canonicalUrl());
                    if (seedRevision != null) {
                        put(metadata, "publisher", seedRevision.publisher());
                        put(metadata, "publishedAt", seedRevision.publishedAt());
                        put(metadata, "modifiedAt", seedRevision.modifiedAt());
                        put(metadata, "retrievedAt", seedRevision.retrievedAt());
                    }
                    metadata.put("workspaceId", source.workspaceId());
                    metadata.put("evidenceOrigin", "INDEXED_WEB");
                    metadata.put("sourceType", "INDEXED_WEB");
                    metadata.put("pageCount", corpus.pageCount());
                    metadata.put("corpusRevisionId", corpus.corpusRevisionId());
                    metadata.put("crawlPolicyHash", corpus.policyHash());
                    return new ResolvedIndexedRagSource(
                            WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE,
                            source.sourceId(),
                            corpus.corpusRevisionId(),
                            WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE,
                            source.sourceId(),
                            corpus.manifestHash(),
                            source.embeddingDeploymentId(),
                            source.embeddingSpaceId(),
                            partitions,
                            metadata);
                });
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
