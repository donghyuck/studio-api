package studio.one.application.webknowledge.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCorpusPageEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCorpusPageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCorpusRevisionEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCorpusRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlItemEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlItemJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageRevisionEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.application.webknowledge.infrastructure.web.WebPageFetchException;
import studio.one.application.webknowledge.infrastructure.web.WebUrlPolicy;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.service.pipeline.RagChunkStage;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagEmbeddingSelection;
import studio.one.platform.ai.service.pipeline.EmbeddingProviderTimeoutException;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class WebKnowledgeSiteCrawlCoordinator {

    public static final List<String> ACTIVE_STATUSES =
            List.of("PENDING", "DISCOVERING", "FETCHING", "INDEXING");
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private static final int MAX_NESTED_SITEMAP_DEPTH = 2;
    private static final int MAX_SITEMAPS_PER_RUN = 10;

    private final WebKnowledgeSourceJpaRepository sources;
    private final WebKnowledgeCrawlRunJpaRepository runs;
    private final WebKnowledgeCrawlItemJpaRepository items;
    private final WebKnowledgePageJpaRepository pages;
    private final WebKnowledgePageRevisionJpaRepository pageRevisions;
    private final WebKnowledgeCorpusRevisionJpaRepository corpusRevisions;
    private final WebKnowledgeCorpusPageJpaRepository corpusPages;
    private final WebPageFetchPort fetchPort;
    private final WebSiteDiscoveryPort discovery;
    private final WebSitemapParser sitemapParser;
    private final WebCrawlUrlPolicy urlPolicy;
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
    private final WebKnowledgeQuotaService quotaService;
    private final WebKnowledgeCrawlStatePersistence statePersistence;
    private final ObjectMapper objectMapper;
    private final Limits activeRunLimits;

    public WebKnowledgeSiteCrawlCoordinator(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeCrawlRunJpaRepository runs,
            WebKnowledgeCrawlItemJpaRepository items,
            WebKnowledgePageJpaRepository pages,
            WebKnowledgePageRevisionJpaRepository pageRevisions,
            WebKnowledgeCorpusRevisionJpaRepository corpusRevisions,
            WebKnowledgeCorpusPageJpaRepository corpusPages,
            WebPageFetchPort fetchPort,
            WebSiteDiscoveryPort discovery,
            WebSitemapParser sitemapParser,
            WebCrawlUrlPolicy urlPolicy,
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
            WebKnowledgeQuotaService quotaService,
            WebKnowledgeCrawlStatePersistence statePersistence,
            ObjectMapper objectMapper,
            Limits activeRunLimits) {
        this.sources = sources;
        this.runs = runs;
        this.items = items;
        this.pages = pages;
        this.pageRevisions = pageRevisions;
        this.corpusRevisions = corpusRevisions;
        this.corpusPages = corpusPages;
        this.fetchPort = fetchPort;
        this.discovery = discovery;
        this.sitemapParser = sitemapParser;
        this.urlPolicy = urlPolicy;
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
        this.quotaService = quotaService;
        this.statePersistence = statePersistence;
        this.objectMapper = objectMapper;
        this.activeRunLimits = activeRunLimits == null ? Limits.defaults() : activeRunLimits;
    }

    @Transactional
    public WebKnowledgeCrawlRunEntity createRun(
            WebKnowledgeSourceEntity source,
            ResolvedWebCrawlPolicy policy,
            String requestedBy,
            String retryOfRunId) {
        if (!ragPipeline.supportsObjectPartitions()) {
            throw new IllegalStateException("WEB_SITE_CRAWL_PARTITION_UNSUPPORTED");
        }
        assertRunCapacity(source.workspaceId(), requestedBy);
        if (quotaService != null) {
            quotaService.reserve(source.workspaceId(), policy.maxPages(), policy.maxTotalNormalizedChars());
        }
        Instant now = Instant.now();
        WebKnowledgeCrawlRunEntity run = new WebKnowledgeCrawlRunEntity(
                "wrun-" + UUID.randomUUID(),
                source.workspaceId(),
                source.sourceId(),
                retryOfRunId,
                write(policy),
                sha256(write(policy)),
                normalize(requestedBy),
                now);
        run.quotaReservation(policy.maxPages(), policy.maxTotalNormalizedChars(), now);
        return runs.save(run);
    }

    public void requestCancel(Long workspaceId, String sourceId) {
        latestRun(workspaceId, sourceId).ifPresent(run -> {
            if (ACTIVE_STATUSES.contains(run.status())) {
                run.requestCancel(Instant.now());
                runs.save(run);
            }
        });
    }

    public java.util.Optional<WebKnowledgeCrawlRunEntity> latestRun(Long workspaceId, String sourceId) {
        return runs.findFirstBySourceIdAndWorkspaceIdOrderByCreatedAtDesc(sourceId, workspaceId);
    }

    public List<WebKnowledgeCrawlRunEntity> listRuns(Long workspaceId, String sourceId) {
        return runs.findBySourceIdAndWorkspaceIdOrderByCreatedAtDesc(sourceId, workspaceId);
    }

    public WebKnowledgeCrawlRunEntity getRun(Long workspaceId, String sourceId, String runId) {
        return runs.findByRunIdAndSourceIdAndWorkspaceId(runId, sourceId, workspaceId)
                .orElseThrow(() -> new NoSuchElementException("WEB_CRAWL_RUN_NOT_FOUND"));
    }

    public List<WebKnowledgePageJpaRepository.PageSummary> listPages(Long workspaceId, String sourceId) {
        return pages.findSummaries(workspaceId, sourceId);
    }

    public List<String> resumableRunIds(Instant now) {
        Instant effectiveNow = now == null ? Instant.now() : now;
        return runs.findByStatusInOrderByCreatedAtAsc(ACTIVE_STATUSES).stream()
                .filter(run -> run.leaseOwner() == null
                        || run.leaseExpiresAt() == null
                        || !run.leaseExpiresAt().isAfter(effectiveNow))
                .map(WebKnowledgeCrawlRunEntity::runId)
                .toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void execute(String runId) {
        WebKnowledgeCrawlRunEntity run = runs.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("WEB_CRAWL_RUN_NOT_FOUND"));
        if (!ACTIVE_STATUSES.contains(run.status())) {
            return;
        }
        Instant claimTime = Instant.now();
        if (run.leaseOwner() != null
                && run.leaseExpiresAt() != null
                && run.leaseExpiresAt().isAfter(claimTime)) {
            return;
        }
        WebKnowledgeSourceEntity source = sources.findById(run.sourceId())
                .filter(candidate -> !candidate.archived())
                .filter(candidate -> candidate.workspaceId().equals(run.workspaceId()))
                .orElseThrow(() -> new NoSuchElementException("WEB_SOURCE_NOT_FOUND"));
        ResolvedWebCrawlPolicy policy = readPolicy(run.policyJson());
        String leaseOwner = "web-crawl-" + UUID.randomUUID();
        Instant started = claimTime;
        run.start(leaseOwner, started.plus(LEASE_DURATION), started);
        source.status("FETCHING", started);
        statePersistence.start(
                run.runId(),
                source.sourceId(),
                leaseOwner,
                started.plus(LEASE_DURATION),
                started);

        Progress progress = new Progress();
        Set<String> seenPageUrlHashes = new LinkedHashSet<>();
        boolean seedSucceeded = false;
        try {
            URI seedUri = WebUrlPolicy.normalize(source.normalizedUrl());
            ArrayDeque<WebKnowledgeCrawlItemEntity> frontier =
                    statePersistence.execute(() -> restoreOrSeed(run, source, seedUri, progress));
            for (WebKnowledgeCrawlItemEntity existing :
                    items.findByRunIdOrderByDiscoveryOrder(run.runId())) {
                if ("INDEXED".equals(existing.status()) || "UNCHANGED".equals(existing.status())) {
                    seenPageUrlHashes.add(existing.normalizedUrlHash());
                    seedSucceeded |= existing.depth() == 0;
                }
            }
            if (policy.discoveryMode() != WebCrawlDiscoveryMode.LINKS_ONLY) {
                statePersistence.execute(
                        () -> discoverSitemaps(run, source, seedUri, policy, frontier, progress));
            }
            run.status("FETCHING", Instant.now());
            persistRun(run, source, progress);

            while (!frontier.isEmpty()) {
                if (cancelled(run)) {
                    run.complete("CANCELLED", Instant.now());
                    source.status("CANCELLED", Instant.now());
                    persistRun(run, source, progress);
                    return;
                }
                if (Duration.between(started, Instant.now()).compareTo(policy.maxRunDuration()) > 0) {
                    run.truncate("MAX_RUN_DURATION", Instant.now());
                    break;
                }
                WebKnowledgeCrawlItemEntity item = frontier.removeFirst();
                if (!"DISCOVERED".equals(item.status())) {
                    continue;
                }
                if (item.discoveryOrder() >= policy.maxPages()) {
                    statePersistence.execute(() -> {
                        item.status("SKIPPED", Instant.now());
                        items.save(item);
                    });
                    progress.skipped++;
                    run.truncate("MAX_PAGES", Instant.now());
                    continue;
                }
                PageProcessingResult result;
                try {
                    result = statePersistence.execute(
                            () -> processPage(run, source, seedUri, item, policy, progress));
                    seedSucceeded |= item.depth() == 0;
                    seenPageUrlHashes.add(item.normalizedUrlHash());
                    seenPageUrlHashes.add(WebUrlPolicy.sha256(result.page().normalizedUrl()));
                } catch (RuntimeException ex) {
                    if ("PAGE_REMOVED".equals(errorCode(ex)) && item.depth() > 0) {
                        statePersistence.execute(() -> {
                            pages.findByWorkspaceIdAndSourceIdAndNormalizedUrlHash(
                                            source.workspaceId(), source.sourceId(), item.normalizedUrlHash())
                                    .ifPresent(page -> {
                                        page.deactivate(Instant.now());
                                        pages.save(page);
                                    });
                            item.status("REMOVED", Instant.now());
                            items.save(item);
                        });
                        progress.removed++;
                        persistRun(run, source, progress);
                        continue;
                    }
                    statePersistence.execute(() -> {
                        item.fail(errorCode(ex), Instant.now());
                        items.save(item);
                    });
                    progress.failed++;
                    if (item.depth() == 0) {
                        throw ex;
                    }
                    persistRun(run, source, progress);
                    continue;
                }
                if (policy.discoveryMode() != WebCrawlDiscoveryMode.SITEMAP_ONLY
                        && item.depth() < policy.maxDepth()) {
                    statePersistence.execute(() -> enqueueLinks(
                                    run,
                                    source,
                                    seedUri,
                                    item,
                                    result.links(),
                                    policy,
                                    frontier,
                                    progress));
                }
                persistRun(run, source, progress);
            }

            if (!seedSucceeded) {
                throw new IllegalStateException("WEB_CRAWL_SEED_FAILED");
            }
            boolean completeDiscovery = !run.truncated() && progress.failed == 0;
            if (completeDiscovery) {
                progress.removed += statePersistence.execute(
                        () -> applyMissingPages(source, seenPageUrlHashes));
            }
            statePersistence.execute(() -> promoteCorpus(run, source, progress));
        } catch (RuntimeException ex) {
            run.fail(errorCode(ex), Instant.now());
            source.status("FAILED", Instant.now());
            persistRun(run, source, progress);
            throw new IllegalStateException("Web site crawl failed: " + run.errorCode(), ex);
        } finally {
            if (quotaService != null) {
                statePersistence.execute(() -> quotaService.releaseReservation(run.runId()));
            }
        }
    }

    private ArrayDeque<WebKnowledgeCrawlItemEntity> restoreOrSeed(
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgeSourceEntity source,
            URI seedUri,
            Progress progress) {
        List<WebKnowledgeCrawlItemEntity> existing = items.findByRunIdOrderByDiscoveryOrder(run.runId());
        if (!existing.isEmpty()) {
            progress.discovered = existing.size();
            ArrayDeque<WebKnowledgeCrawlItemEntity> restored = new ArrayDeque<>();
            for (WebKnowledgeCrawlItemEntity item : existing) {
                switch (item.status()) {
                    case "INDEXED" -> {
                        progress.indexed++;
                        progress.updated++;
                    }
                    case "UNCHANGED" -> progress.unchanged++;
                    case "FAILED" -> progress.failed++;
                    case "SKIPPED" -> progress.skipped++;
                    case "REMOVED" -> progress.removed++;
                    case "FETCHING", "INDEXING" -> {
                        item.status("DISCOVERED", Instant.now());
                        items.save(item);
                        restored.addLast(item);
                    }
                    case "DISCOVERED" -> restored.addLast(item);
                    default -> {
                    }
                }
            }
            return restored;
        }
        WebKnowledgeCrawlItemEntity seed = new WebKnowledgeCrawlItemEntity(
                "witem-" + UUID.randomUUID(),
                source.workspaceId(),
                run.runId(),
                source.sourceId(),
                seedUri.toString(),
                WebUrlPolicy.sha256(seedUri.toString()),
                null,
                0,
                0,
                Instant.now());
        items.save(seed);
        progress.discovered = 1;
        return new ArrayDeque<>(List.of(seed));
    }

    private void discoverSitemaps(
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgeSourceEntity source,
            URI seedUri,
            ResolvedWebCrawlPolicy policy,
            ArrayDeque<WebKnowledgeCrawlItemEntity> frontier,
            Progress progress) {
        ArrayDeque<SitemapCandidate> sitemapFrontier = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        URI robotsUri = originUri(seedUri, "/robots.txt");
        awaitOrigin(seedUri, policy.minDelayPerOrigin());
        WebPageFetchPort.FetchResult robots = fetchPort.fetch(
                robotsUri, WebPageFetchPort.ConditionalRequest.none(), WebPageFetchPort.ResourceKind.ROBOTS);
        if (robots.statusCode() == 200) {
            String robotsText = new String(robots.body(), StandardCharsets.UTF_8);
            robotsText.lines()
                    .map(String::trim)
                    .filter(line -> line.regionMatches(true, 0, "sitemap:", 0, "sitemap:".length()))
                    .map(line -> line.substring(line.indexOf(':') + 1).trim())
                    .map(value -> sameOriginSitemap(seedUri, value))
                    .flatMap(java.util.Optional::stream)
                    .forEach(uri -> sitemapFrontier.add(new SitemapCandidate(uri, 0)));
        }
        URI conventional = originUri(seedUri, "/sitemap.xml");
        sitemapFrontier.add(new SitemapCandidate(conventional, 0));

        while (!sitemapFrontier.isEmpty() && visited.size() < MAX_SITEMAPS_PER_RUN) {
            SitemapCandidate candidate = sitemapFrontier.removeFirst();
            if (candidate.depth() > MAX_NESTED_SITEMAP_DEPTH || !visited.add(candidate.uri().toString())) {
                continue;
            }
            awaitOrigin(candidate.uri(), policy.minDelayPerOrigin());
            WebPageFetchPort.FetchResult fetched = fetchPort.fetch(
                    candidate.uri(),
                    WebPageFetchPort.ConditionalRequest.none(),
                    WebPageFetchPort.ResourceKind.SITEMAP);
            progress.responseBytes += fetched.compressedBytes();
            if (fetched.statusCode() == 404) {
                continue;
            }
            WebSitemapParser.ParsedSitemap parsed = sitemapParser.parse(fetched.body());
            for (String location : parsed.pageLocations()) {
                urlPolicy.candidate(seedUri, candidate.uri(), location, policy)
                        .ifPresent(uri -> enqueue(
                                run,
                                source,
                                uri,
                                null,
                                1,
                                policy,
                                frontier,
                                progress));
            }
            for (String nested : parsed.sitemapLocations()) {
                sameOriginSitemap(seedUri, nested).ifPresent(uri ->
                        sitemapFrontier.add(new SitemapCandidate(uri, candidate.depth() + 1)));
            }
        }
        if (!sitemapFrontier.isEmpty()) {
            run.truncate("MAX_SITEMAPS", Instant.now());
        }
    }

    private PageProcessingResult processPage(
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgeSourceEntity source,
            URI seedUri,
            WebKnowledgeCrawlItemEntity item,
            ResolvedWebCrawlPolicy policy,
            Progress progress) {
        URI requestedUri = URI.create(item.normalizedUrl());
        awaitOrigin(requestedUri, policy.minDelayPerOrigin());
        item.status("FETCHING", Instant.now());
        items.save(item);
        WebKnowledgePageEntity page = pages
                .findByWorkspaceIdAndSourceIdAndNormalizedUrlHash(
                        source.workspaceId(), source.sourceId(), item.normalizedUrlHash())
                .orElse(null);
        WebKnowledgePageRevisionEntity current = page == null || page.currentPageRevisionId() == null
                ? null
                : pageRevisions.findByPageRevisionIdAndWorkspaceIdAndSourceId(
                                page.currentPageRevisionId(), source.workspaceId(), source.sourceId())
                        .orElse(null);
        boolean linksRequired = policy.discoveryMode() != WebCrawlDiscoveryMode.SITEMAP_ONLY
                && item.depth() < policy.maxDepth();
        WebPageFetchPort.ConditionalRequest conditional = current == null || linksRequired
                ? WebPageFetchPort.ConditionalRequest.none()
                : new WebPageFetchPort.ConditionalRequest(current.etag(), current.lastModified());
        WebPageFetchPort.FetchResult fetched = fetchPort.fetch(
                requestedUri,
                conditional,
                WebPageFetchPort.ResourceKind.PAGE);
        progress.fetched++;
        progress.responseBytes += fetched.compressedBytes();
        persistRun(run, source, progress);
        if (progress.responseBytes > policy.maxTotalResponseBytes()) {
            throw new IllegalStateException("WEB_CRAWL_RESPONSE_BUDGET_EXCEEDED");
        }

        URI finalUri = urlPolicy.candidate(seedUri, requestedUri, fetched.finalUri().toString(), policy)
                .orElseThrow(() -> new IllegalStateException("WEB_CRAWL_REDIRECT_OUT_OF_SCOPE"));
        if (page == null) {
            page = new WebKnowledgePageEntity(
                    "wpage-" + UUID.randomUUID(),
                    source.workspaceId(),
                    source.sourceId(),
                    item.normalizedUrl(),
                    item.normalizedUrlHash(),
                    Instant.now());
        }
        if (fetched.notModified()) {
            if (current == null) {
                throw new IllegalStateException("WEB_CRAWL_NOT_MODIFIED_WITHOUT_REVISION");
            }
            page.seen(
                    page.canonicalUrl() == null ? finalUri.toString() : page.canonicalUrl(),
                    sha256(page.canonicalUrl() == null ? finalUri.toString() : page.canonicalUrl()),
                    Instant.now());
            pages.save(page);
            item.status("UNCHANGED", Instant.now());
            items.save(item);
            progress.unchanged++;
            return new PageProcessingResult(page, current, List.of());
        }

        WebPageMetadataExtractor.Metadata pageMetadata = sanitizeMetadata(
                metadataExtractor.extract(fetched.body(), finalUri), seedUri, finalUri, policy);
        String canonicalHash = sha256(pageMetadata.canonicalUri().toString());
        String currentPageId = page.pageId();
        WebKnowledgePageEntity canonicalPage = pages
                .findFirstByWorkspaceIdAndSourceIdAndCanonicalUrlHashAndActiveTrue(
                        source.workspaceId(), source.sourceId(), canonicalHash)
                .filter(candidate -> !candidate.pageId().equals(currentPageId))
                .orElse(null);
        if (canonicalPage != null && canonicalPage.currentPageRevisionId() != null) {
            WebKnowledgePageRevisionEntity canonicalRevision = pageRevisions
                    .findByPageRevisionIdAndWorkspaceIdAndSourceId(
                            canonicalPage.currentPageRevisionId(), source.workspaceId(), source.sourceId())
                    .orElse(null);
            if (canonicalRevision != null) {
                canonicalPage.seen(pageMetadata.canonicalUri().toString(), canonicalHash, Instant.now());
                pages.save(canonicalPage);
                item.status("SKIPPED", Instant.now());
                items.save(item);
                progress.skipped++;
                return new PageProcessingResult(
                        canonicalPage,
                        canonicalRevision,
                        discovery.links(fetched.body(), finalUri));
            }
        }
        var parsed = htmlParser.parseStructured(
                fetched.body(), fetched.contentType(), source.host() + ".html");
        NormalizedDocument adapted = contentSanitizer.sanitize(
                normalizedDocumentAdapter.adapt("crawl-" + item.itemId(), parsed));
        if (adapted.chunkableText().isBlank()) {
            throw new IllegalStateException("NO_EXTRACTABLE_CONTENT");
        }
        if (adapted.chunkableText().length() > fetchPolicy.maxNormalizedChars()) {
            throw new IllegalStateException("NORMALIZED_CONTENT_TOO_LARGE");
        }
        progress.normalizedChars += adapted.chunkableText().length();
        persistRun(run, source, progress);
        if (progress.normalizedChars > policy.maxTotalNormalizedChars()) {
            throw new IllegalStateException("WEB_CRAWL_NORMALIZED_BUDGET_EXCEEDED");
        }

        List<String> links = discovery.links(fetched.body(), finalUri);
        String contentHash = sha256(adapted.chunkableText());
        WebKnowledgePageRevisionEntity duplicateRevision = pageRevisions
                .findFirstByWorkspaceIdAndSourceIdAndContentHashAndStatusOrderByCreatedAtAsc(
                        source.workspaceId(), source.sourceId(), contentHash, "COMPLETED")
                .filter(candidate -> current == null
                        || !candidate.pageRevisionId().equals(current.pageRevisionId()))
                .orElse(null);
        if (duplicateRevision != null) {
            WebKnowledgePageEntity duplicatePage = pages
                    .findByPageIdAndWorkspaceIdAndSourceId(
                            duplicateRevision.pageId(), source.workspaceId(), source.sourceId())
                    .filter(WebKnowledgePageEntity::active)
                    .orElse(null);
            if (duplicatePage != null) {
                duplicatePage.seen(
                        duplicatePage.canonicalUrl() == null
                                ? pageMetadata.canonicalUri().toString()
                                : duplicatePage.canonicalUrl(),
                        duplicatePage.canonicalUrl() == null
                                ? canonicalHash
                                : sha256(duplicatePage.canonicalUrl()),
                        Instant.now());
                pages.save(duplicatePage);
                item.status("SKIPPED", Instant.now());
                items.save(item);
                progress.skipped++;
                return new PageProcessingResult(duplicatePage, duplicateRevision, links);
            }
        }
        page.seen(
                pageMetadata.canonicalUri().toString(),
                sha256(pageMetadata.canonicalUri().toString()),
                Instant.now());
        pages.save(page);
        if (current != null && contentHash.equals(current.contentHash())) {
            item.status("UNCHANGED", Instant.now());
            items.save(item);
            progress.unchanged++;
            return new PageProcessingResult(page, current, links);
        }

        String pageRevisionId = "wprev-" + UUID.randomUUID();
        WebKnowledgePageRevisionEntity revision = new WebKnowledgePageRevisionEntity(
                pageRevisionId,
                source.workspaceId(),
                source.sourceId(),
                page.pageId(),
                run.runId(),
                Instant.now());
        revision.fetched(
                fetched.contentType(),
                fetched.body().length,
                bounded(fetched.etag(), 500),
                bounded(fetched.lastModified(), 500),
                fetched.retrievedAt(),
                Instant.now());
        Map<String, Object> metadata = metadata(
                source, run, page, revision, fetched, pageMetadata, contentHash);
        NormalizedDocument document = new NormalizedDocument(
                pageRevisionId,
                adapted.plainText(),
                adapted.sourceFormat(),
                adapted.filename(),
                adapted.blocks(),
                merge(adapted.metadata(), metadata));
        revision.normalized(
                pageMetadata.title(),
                pageMetadata.publisher(),
                pageMetadata.language(),
                pageMetadata.publishedAt(),
                pageMetadata.modifiedAt(),
                contentHash,
                write(document),
                bounded(document.chunkableText(), 500),
                write(pageMetadata.values()),
                Instant.now());
        pageRevisions.save(revision);

        run.status("INDEXING", Instant.now());
        persistRun(run, source, progress);
        indexPage(source, page, revision, document, metadata);
        revision.complete(Instant.now());
        pageRevisions.save(revision);
        page.currentRevision(revision.pageRevisionId(), Instant.now());
        pages.save(page);
        item.indexed(page.pageId(), revision.pageRevisionId(), Instant.now());
        items.save(item);
        progress.indexed++;
        if (current == null) {
            progress.updated++;
        } else {
            progress.updated++;
        }
        return new PageProcessingResult(page, revision, links);
    }

    private void indexPage(
            WebKnowledgeSourceEntity source,
            WebKnowledgePageEntity page,
            WebKnowledgePageRevisionEntity revision,
            NormalizedDocument document,
            Map<String, Object> metadata) {
        List<Chunk> chunks = chunking.chunk(document);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("NO_CHUNKS");
        }
        String chunkSetId = "wcset-" + UUID.randomUUID();
        String strategyHash = sha256("web-site:structure-based:v1");
        List<RagChunkStage> stages = new ArrayList<>(chunks.size());
        List<ChunkSetItem> chunkItems = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            Map<String, Object> chunkMetadata = new LinkedHashMap<>(metadata);
            chunkMetadata.putAll(chunk.metadata().toMap());
            chunkMetadata.put("chunkSetId", chunkSetId);
            chunkMetadata.put("partitionId", revision.pageRevisionId());
            chunkMetadata.put("ragRechunkApplied", false);
            stages.add(new RagChunkStage(
                    WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE,
                    source.sourceId(),
                    revision.pageRevisionId(),
                    index,
                    chunk.id(),
                    chunk.content(),
                    chunkMetadata,
                    null));
            chunkItems.add(new ChunkSetItem(
                    index,
                    chunk.id(),
                    chunk.content(),
                    sha256(chunk.content()),
                    chunkMetadata));
        }
        stageStore.replace(
                WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE,
                source.sourceId(),
                revision.pageRevisionId(),
                stages);
        Instant now = Instant.now();
        chunkSetStore.save(new ChunkSet(
                chunkSetId,
                WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE,
                source.sourceId(),
                revision.pageRevisionId(),
                revision.pageRevisionId(),
                revision.contentHash(),
                "structure-based",
                strategyHash,
                null,
                null,
                null,
                ChunkSetStatus.READY,
                ChunkSetQualityStatus.VALID,
                List.of(),
                metadata,
                chunkItems,
                now,
                now));
        ragPipeline.indexObjectPartition(
                new RagIndexRequest(
                        revision.pageRevisionId(),
                        document.chunkableText(),
                        merge(metadata, Map.of(
                                "chunkSetId", chunkSetId,
                                "partitionId", revision.pageRevisionId(),
                                "requirePreparedChunks", true,
                                "ragRechunkApplied", false)),
                        List.of(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        source.embeddingDeploymentId()),
                WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE,
                source.sourceId(),
                revision.pageRevisionId(),
                RagIndexProgressListener.noop());
    }

    private void enqueueLinks(
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgeSourceEntity source,
            URI seedUri,
            WebKnowledgeCrawlItemEntity parent,
            List<String> links,
            ResolvedWebCrawlPolicy policy,
            ArrayDeque<WebKnowledgeCrawlItemEntity> frontier,
            Progress progress) {
        for (String link : links) {
            urlPolicy.candidate(seedUri, URI.create(parent.normalizedUrl()), link, policy)
                    .ifPresent(uri -> enqueue(
                            run,
                            source,
                            uri,
                            parent.normalizedUrlHash(),
                            parent.depth() + 1,
                            policy,
                            frontier,
                            progress));
        }
    }

    private void enqueue(
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgeSourceEntity source,
            URI uri,
            String parentUrlHash,
            int depth,
            ResolvedWebCrawlPolicy policy,
            ArrayDeque<WebKnowledgeCrawlItemEntity> frontier,
            Progress progress) {
        String hash = WebUrlPolicy.sha256(uri.toString());
        if (items.findByRunIdAndNormalizedUrlHash(run.runId(), hash).isPresent()) {
            return;
        }
        if (progress.discovered >= policy.maxPages()) {
            run.truncate("MAX_PAGES", Instant.now());
            return;
        }
        WebKnowledgeCrawlItemEntity item = new WebKnowledgeCrawlItemEntity(
                "witem-" + UUID.randomUUID(),
                source.workspaceId(),
                run.runId(),
                source.sourceId(),
                uri.toString(),
                hash,
                parentUrlHash,
                depth,
                progress.discovered,
                Instant.now());
        items.save(item);
        frontier.addLast(item);
        progress.discovered++;
    }

    private int applyMissingPages(WebKnowledgeSourceEntity source, Set<String> seenUrlHashes) {
        int removed = 0;
        for (WebKnowledgePageEntity page :
                pages.findByWorkspaceIdAndSourceIdAndActiveTrueOrderByNormalizedUrlAsc(
                        source.workspaceId(), source.sourceId())) {
            if (seenUrlHashes.contains(WebUrlPolicy.sha256(page.normalizedUrl()))) {
                continue;
            }
            page.missing(Instant.now());
            if (page.missingRunCount() >= 2) {
                page.deactivate(Instant.now());
                removed++;
            }
            pages.save(page);
        }
        return removed;
    }

    private void promoteCorpus(
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgeSourceEntity source,
            Progress progress) {
        List<WebKnowledgePageEntity> activePages =
                pages.findByWorkspaceIdAndSourceIdAndActiveTrueOrderByNormalizedUrlAsc(
                        source.workspaceId(), source.sourceId());
        List<ManifestEntry> manifest = activePages.stream()
                .filter(page -> page.currentPageRevisionId() != null)
                .map(page -> pageRevisions.findByPageRevisionIdAndWorkspaceIdAndSourceId(
                                page.currentPageRevisionId(), source.workspaceId(), source.sourceId())
                        .map(revision -> new ManifestEntry(page, revision))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(entry -> entry.page().normalizedUrl()))
                .toList();
        if (manifest.isEmpty()) {
            throw new IllegalStateException("WEB_CRAWL_NO_USABLE_PAGES");
        }
        String manifestHash = sha256(manifest.stream()
                .map(entry -> entry.page().pageId()
                        + "|" + entry.revision().pageRevisionId()
                        + "|" + entry.revision().contentHash())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(""));
        if (source.currentCorpusRevisionId() != null) {
            var current = corpusRevisions.findByCorpusRevisionIdAndWorkspaceIdAndSourceIdAndStatus(
                    source.currentCorpusRevisionId(), source.workspaceId(), source.sourceId(), "COMPLETED");
            if (current.isPresent() && manifestHash.equals(current.get().manifestHash())) {
                run.complete(progress.failed > 0 ? "PARTIAL" : "UNCHANGED", Instant.now());
                source.unchanged(Instant.now());
                persistRun(run, source, progress);
                return;
            }
        }
        String corpusRevisionId = "wcorpus-" + UUID.randomUUID();
        WebKnowledgeCorpusRevisionEntity corpus = new WebKnowledgeCorpusRevisionEntity(
                corpusRevisionId,
                source.workspaceId(),
                source.sourceId(),
                run.runId(),
                manifestHash,
                run.policyHash(),
                source.embeddingSpaceId(),
                manifest.size(),
                Instant.now());
        corpusRevisions.save(corpus);
        List<WebKnowledgeCorpusPageEntity> manifestRows = new ArrayList<>(manifest.size());
        for (int index = 0; index < manifest.size(); index++) {
            ManifestEntry entry = manifest.get(index);
            manifestRows.add(new WebKnowledgeCorpusPageEntity(
                    "wcp-" + UUID.randomUUID(),
                    source.workspaceId(),
                    source.sourceId(),
                    corpusRevisionId,
                    entry.page().pageId(),
                    entry.revision().pageRevisionId(),
                    index,
                    Instant.now()));
        }
        corpusPages.saveAll(manifestRows);
        corpus.complete(Instant.now());
        corpusRevisions.save(corpus);
        ManifestEntry seedEntry = manifest.stream()
                .filter(entry -> entry.page().normalizedUrlHash().equals(source.normalizedUrlHash()))
                .findFirst()
                .orElse(manifest.get(0));
        source.completeCorpus(
                corpusRevisionId,
                seedEntry.page().canonicalUrl(),
                source.embeddingSpaceId(),
                seedEntry.revision().title(),
                Instant.now());
        run.complete(progress.failed > 0 || run.truncated() ? "PARTIAL" : "COMPLETED", Instant.now());
        persistRun(run, source, progress);
    }

    private WebPageMetadataExtractor.Metadata sanitizeMetadata(
            WebPageMetadataExtractor.Metadata metadata,
            URI seedUri,
            URI fetchedUri,
            ResolvedWebCrawlPolicy policy) {
        URI canonical = urlPolicy
                .candidate(seedUri, fetchedUri, metadata.canonicalUri().toString(), policy)
                .orElse(fetchedUri);
        Map<String, Object> values = new LinkedHashMap<>(
                contentSanitizer.sanitizeMetadata(metadata.values()));
        String title = contentSanitizer.sanitizeText(metadata.title());
        String publisher = contentSanitizer.sanitizeText(metadata.publisher());
        values.put("canonicalUrl", canonical.toString());
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
                canonical,
                Map.copyOf(values));
    }

    private Map<String, Object> metadata(
            WebKnowledgeSourceEntity source,
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgePageEntity page,
            WebKnowledgePageRevisionEntity revision,
            WebPageFetchPort.FetchResult fetched,
            WebPageMetadataExtractor.Metadata pageMetadata,
            String contentHash) {
        Map<String, Object> metadata = new HashMap<>(pageMetadata.values());
        metadata.put("objectType", WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE);
        metadata.put("objectId", source.sourceId());
        metadata.put("sourceType", "WEB_PAGE");
        metadata.put("sourceRevisionId", revision.pageRevisionId());
        metadata.put("pageRevisionId", revision.pageRevisionId());
        metadata.put("partitionId", revision.pageRevisionId());
        metadata.put("pageId", page.pageId());
        metadata.put("crawlRunId", run.runId());
        metadata.put("evidenceOrigin", "INDEXED_WEB");
        metadata.put("contentHash", contentHash);
        metadata.put("canonicalUrl", pageMetadata.canonicalUri().toString());
        metadata.put("retrievedAt", fetched.retrievedAt().toString());
        if (source.displayName() != null) {
            metadata.put("displayName", source.displayName());
        }
        return Map.copyOf(metadata);
    }

    private void assertRunCapacity(Long workspaceId, String requestedBy) {
        if (runs.countByStatusIn(ACTIVE_STATUSES) >= activeRunLimits.global()) {
            throw new IllegalStateException("WEB_CRAWL_GLOBAL_LIMIT_EXCEEDED");
        }
        if (runs.countByWorkspaceIdAndStatusIn(workspaceId, ACTIVE_STATUSES)
                >= activeRunLimits.perWorkspace()) {
            throw new IllegalStateException("WEB_CRAWL_WORKSPACE_LIMIT_EXCEEDED");
        }
        if (requestedBy != null
                && runs.countByRequestedByAndStatusIn(requestedBy, ACTIVE_STATUSES)
                        >= activeRunLimits.perPrincipal()) {
            throw new IllegalStateException("WEB_CRAWL_PRINCIPAL_LIMIT_EXCEEDED");
        }
    }

    private boolean cancelled(WebKnowledgeCrawlRunEntity run) {
        WebKnowledgeCrawlRunEntity current = runs.findById(run.runId()).orElse(run);
        return current.cancelRequestedAt() != null;
    }

    private void persistRun(
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgeSourceEntity source,
            Progress progress) {
        Instant now = Instant.now();
        run.progress(
                progress.discovered,
                progress.fetched,
                progress.indexed,
                progress.unchanged,
                progress.updated,
                progress.removed,
                progress.failed,
                progress.skipped,
                progress.responseBytes,
                progress.normalizedChars,
                now);
        if (ACTIVE_STATUSES.contains(run.status())) {
            run.heartbeat(run.leaseOwner(), now.plus(LEASE_DURATION), now);
        }
        statePersistence.save(
                run,
                source,
                new WebKnowledgeCrawlProgressSnapshot(
                        progress.discovered,
                        progress.fetched,
                        progress.indexed,
                        progress.unchanged,
                        progress.updated,
                        progress.removed,
                        progress.failed,
                        progress.skipped,
                        progress.responseBytes,
                        progress.normalizedChars),
                now,
                now.plus(LEASE_DURATION));
    }

    private ResolvedWebCrawlPolicy readPolicy(String json) {
        try {
            return objectMapper.readValue(json, ResolvedWebCrawlPolicy.class);
        } catch (Exception ex) {
            throw new IllegalStateException("WEB_CRAWL_POLICY_INVALID", ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("WEB_CRAWL_SERIALIZATION_FAILED", ex);
        }
    }

    private static java.util.Optional<URI> sameOriginSitemap(URI seed, String value) {
        try {
            URI parsed = URI.create(value);
            URI candidate = WebUrlPolicy.normalize(
                    (parsed.isAbsolute() ? parsed : seed.resolve(parsed)).toString());
            if (!seed.getHost().equalsIgnoreCase(candidate.getHost())
                    || effectivePort(seed) != effectivePort(candidate)) {
                return java.util.Optional.empty();
            }
            WebUrlPolicy.assertPublicHost(candidate);
            return java.util.Optional.of(candidate);
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    private static URI originUri(URI source, String path) {
        try {
            return new URI("https", null, source.getHost(), source.getPort(), path, null, null);
        } catch (Exception ex) {
            throw new IllegalStateException("WEB_CRAWL_URL_INVALID", ex);
        }
    }

    private static int effectivePort(URI value) {
        return value.getPort() < 0 ? 443 : value.getPort();
    }

    private static void awaitOrigin(URI uri, Duration delay) {
        WebOriginRequestScheduler.await(uri, delay);
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

    private static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String errorCode(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof WebPageFetchException fetch) {
                return fetch.errorCode();
            }
            if (current instanceof EmbeddingProviderTimeoutException) {
                return "WEB_CRAWL_EMBEDDING_TIMEOUT";
            }
            String message = current.getMessage();
            if (message != null && (message.startsWith("WEB_")
                    || "NO_EXTRACTABLE_CONTENT".equals(message)
                    || "NORMALIZED_CONTENT_TOO_LARGE".equals(message)
                    || "NO_CHUNKS".equals(message)
                    || "BLOCKED_MODEL_CONFIGURATION".equals(message))) {
                return bounded(message, 80);
            }
            current = current.getCause();
        }
        return "WEB_CRAWL_PROCESSING_FAILED";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Limits(int global, int perWorkspace, int perPrincipal) {
        public Limits {
            if (global < 1 || perWorkspace < 1 || perPrincipal < 1) {
                throw new IllegalArgumentException("WEB_CRAWL_ACTIVE_LIMITS_INVALID");
            }
        }

        public static Limits defaults() {
            return new Limits(2, 1, 1);
        }
    }

    private record SitemapCandidate(URI uri, int depth) {
    }

    private record PageProcessingResult(
            WebKnowledgePageEntity page,
            WebKnowledgePageRevisionEntity revision,
            List<String> links) {
    }

    private record ManifestEntry(
            WebKnowledgePageEntity page,
            WebKnowledgePageRevisionEntity revision) {
    }

    private static final class Progress {
        int discovered;
        int fetched;
        int indexed;
        int unchanged;
        int updated;
        int removed;
        int failed;
        int skipped;
        long responseBytes;
        long normalizedChars;
    }
}
