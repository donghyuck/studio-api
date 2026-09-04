package studio.one.application.webknowledge.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.application.webknowledge.infrastructure.web.WebUrlPolicy;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagEmbeddingSelection;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class DefaultWebKnowledgeSourceService implements WebKnowledgeSourceService {

    private static final String SOURCE_TYPE = "web_source";

    private final WebKnowledgeSourceJpaRepository sources;
    private final WebKnowledgeRevisionJpaRepository revisions;
    private final RagIndexJobService jobs;
    private final RagPipelineService ragPipeline;
    private final RagEmbeddingProfileResolver embeddingResolver;
    private final WebKnowledgeContentSanitizer contentSanitizer;
    private final Executor executor;
    private final WebCrawlPolicyResolver crawlPolicyResolver;
    private final WebKnowledgeSiteCrawlCoordinator siteCrawlCoordinator;
    private final WebKnowledgeQuotaService quotaService;
    private final ObjectMapper objectMapper;
    private final boolean siteCrawlEnabled;

    public DefaultWebKnowledgeSourceService(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            RagIndexJobService jobs,
            RagPipelineService ragPipeline,
            RagEmbeddingProfileResolver embeddingResolver,
            WebKnowledgeContentSanitizer contentSanitizer,
            Executor executor) {
        this(
                sources,
                revisions,
                jobs,
                ragPipeline,
                embeddingResolver,
                contentSanitizer,
                executor,
                new WebCrawlPolicyResolver(WebCrawlPolicyResolver.Limits.defaults()),
                null,
                null,
                new ObjectMapper(),
                false);
    }

    public DefaultWebKnowledgeSourceService(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            RagIndexJobService jobs,
            RagPipelineService ragPipeline,
            RagEmbeddingProfileResolver embeddingResolver,
            WebKnowledgeContentSanitizer contentSanitizer,
            Executor executor,
            WebCrawlPolicyResolver crawlPolicyResolver,
            WebKnowledgeSiteCrawlCoordinator siteCrawlCoordinator,
            WebKnowledgeQuotaService quotaService,
            ObjectMapper objectMapper,
            boolean siteCrawlEnabled) {
        this.sources = sources;
        this.revisions = revisions;
        this.jobs = jobs;
        this.ragPipeline = ragPipeline;
        this.embeddingResolver = embeddingResolver;
        this.contentSanitizer = contentSanitizer;
        this.executor = executor;
        this.crawlPolicyResolver = crawlPolicyResolver;
        this.siteCrawlCoordinator = siteCrawlCoordinator;
        this.quotaService = quotaService;
        this.objectMapper = objectMapper;
        this.siteCrawlEnabled = siteCrawlEnabled;
    }

    @Override
    @Transactional
    public WebKnowledgeSourceView create(
            Long workspaceId,
            WebKnowledgeSourceCreateCommand command,
            String principal) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        URI normalized = WebUrlPolicy.normalize(command.url());
        String deploymentId = required(command.embeddingDeploymentId(), "embeddingDeploymentId");
        WebKnowledgeCollectionMode collectionMode = WebKnowledgeCollectionMode.from(command.collectionMode());
        ResolvedWebCrawlPolicy crawlPolicy = crawlPolicyResolver.resolve(collectionMode, command.crawlPolicy());
        assertCollectionModeAvailable(collectionMode);
        String urlHash = WebUrlPolicy.sha256(normalized.toString());
        var existing = sources
                .findByWorkspaceIdAndNormalizedUrlHashAndEmbeddingDeploymentIdAndArchivedFalse(
                        workspaceId, urlHash, deploymentId);
        if (existing.isPresent()) {
            if (!collectionMode.name().equals(existing.get().collectionMode())) {
                throw new IllegalStateException("WEB_SOURCE_COLLECTION_MODE_CONFLICT");
            }
            return view(existing.get(), currentRevision(existing.get()));
        }
        if (quotaService != null) {
            quotaService.assertSourceCapacity(workspaceId);
        }
        String embeddingSpaceId;
        try {
            embeddingSpaceId = embeddingResolver.resolve(new RagEmbeddingSelection(
                    null, null, null, null, EmbeddingInputType.TEXT, deploymentId))
                    .embeddingSpaceId();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("embeddingDeploymentId is unavailable", ex);
        }

        Instant now = Instant.now();
        String sourceId = "wsrc-" + UUID.randomUUID();
        WebKnowledgeSourceEntity source = new WebKnowledgeSourceEntity(
                sourceId,
                workspaceId,
                command.url(),
                normalized.toString(),
                urlHash,
                normalized.getHost(),
                contentSanitizer.sanitizeText(normalize(command.displayName())),
                deploymentId,
                embeddingSpaceId,
                collectionMode.name(),
                write(crawlPolicy),
                WebUrlPolicy.sha256(write(crawlPolicy)),
                normalize(principal),
                now);
        sources.save(source);
        return collectionMode == WebKnowledgeCollectionMode.SITE
                ? submitSite(source, crawlPolicy, principal, now)
                : submit(source, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebKnowledgeSourceView> list(Long workspaceId, String embeddingDeploymentId) {
        String deploymentId = normalize(embeddingDeploymentId);
        return sources.findByWorkspaceIdAndArchivedFalseOrderByUpdatedAtDesc(workspaceId).stream()
                .filter(source -> deploymentId == null || deploymentId.equals(source.embeddingDeploymentId()))
                .map(source -> view(source, currentRevision(source)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WebKnowledgeSourceView get(Long workspaceId, String sourceId) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        return view(source, currentRevision(source));
    }

    @Override
    @Transactional
    public WebKnowledgeSourceView refresh(Long workspaceId, String sourceId) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        if (WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())) {
            assertCollectionModeAvailable(WebKnowledgeCollectionMode.SITE);
            if (siteCrawlCoordinator.latestRun(workspaceId, sourceId)
                    .filter(run -> WebKnowledgeSiteCrawlCoordinator.ACTIVE_STATUSES.contains(run.status()))
                    .isPresent()) {
                throw new IllegalStateException("WEB_SOURCE_JOB_ALREADY_ACTIVE");
            }
            ResolvedWebCrawlPolicy policy = readPolicy(source.crawlPolicyJson());
            return submitSite(source, policy, source.createdBy(), Instant.now());
        }
        if (hasActiveRevision(source.sourceId())) {
            throw new IllegalStateException("WEB_SOURCE_JOB_ALREADY_ACTIVE");
        }
        return submit(source, Instant.now());
    }

    @Override
    @Transactional
    public WebKnowledgeSourceView cancel(Long workspaceId, String sourceId) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        if (WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())) {
            assertCollectionModeAvailable(WebKnowledgeCollectionMode.SITE);
            siteCrawlCoordinator.requestCancel(workspaceId, sourceId);
            source.status("CANCEL_REQUESTED", Instant.now());
            sources.save(source);
            return view(source, null);
        }
        WebKnowledgeRevisionEntity revision = latestRevision(source.sourceId());
        if (revision.ragJobId() != null && active(revision.status())) {
            jobs.getJob(revision.ragJobId())
                    .filter(job -> active(job.status()))
                    .ifPresent(job -> jobs.cancelJob(job.jobId()));
            revision.status("CANCELLED", Instant.now());
            source.status("CANCELLED", Instant.now());
            revisions.save(revision);
            sources.save(source);
        }
        return view(source, revision);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebKnowledgeCrawlRunView> listCrawlRuns(Long workspaceId, String sourceId) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        if (!WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())
                || siteCrawlCoordinator == null) {
            return List.of();
        }
        return siteCrawlCoordinator.listRuns(workspaceId, sourceId).stream()
                .map(WebKnowledgeCrawlRunView::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WebKnowledgeCrawlRunView getCrawlRun(Long workspaceId, String sourceId, String runId) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        if (!WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())
                || siteCrawlCoordinator == null) {
            throw new NoSuchElementException("WEB_CRAWL_RUN_NOT_FOUND");
        }
        return WebKnowledgeCrawlRunView.from(siteCrawlCoordinator.getRun(workspaceId, sourceId, runId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebKnowledgePageView> listPages(Long workspaceId, String sourceId) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        if (!WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())
                || siteCrawlCoordinator == null) {
            return List.of();
        }
        return siteCrawlCoordinator.listPages(workspaceId, sourceId).stream()
                .map(WebKnowledgePageView::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WebKnowledgePageDetailView getPage(Long workspaceId, String sourceId, String pageId) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        if (!WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())
                || siteCrawlCoordinator == null) {
            throw new NoSuchElementException("WEB_PAGE_NOT_FOUND");
        }
        return siteCrawlCoordinator.getPage(workspaceId, sourceId, pageId);
    }

    @Override
    @Transactional
    public WebKnowledgeSourceView updateCrawlPolicy(
            Long workspaceId,
            String sourceId,
            WebCrawlPolicyInput policyInput) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        if (!WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())) {
            throw new IllegalStateException("WEB_CRAWL_POLICY_REQUIRES_SITE_SOURCE");
        }
        if (siteCrawlCoordinator.latestRun(workspaceId, sourceId)
                .filter(run -> WebKnowledgeSiteCrawlCoordinator.ACTIVE_STATUSES.contains(run.status()))
                .isPresent()) {
            throw new IllegalStateException("WEB_SOURCE_JOB_ALREADY_ACTIVE");
        }
        ResolvedWebCrawlPolicy resolved =
                crawlPolicyResolver.resolve(WebKnowledgeCollectionMode.SITE, policyInput);
        String json = write(resolved);
        source.crawlPolicy(json, WebUrlPolicy.sha256(json), Instant.now());
        sources.save(source);
        return view(source, null);
    }

    @Override
    @Transactional
    public void archive(Long workspaceId, String sourceId) {
        WebKnowledgeSourceEntity source = requireSource(workspaceId, sourceId);
        if (WebKnowledgeCollectionMode.SITE.name().equals(source.collectionMode())) {
            if (siteCrawlCoordinator != null) {
                siteCrawlCoordinator.requestCancel(workspaceId, sourceId);
            }
        } else if (hasActiveRevision(source.sourceId())) {
            cancel(workspaceId, sourceId);
        }
        ragPipeline.deleteByObject(SOURCE_TYPE, source.sourceId());
        source.archive(Instant.now());
        sources.save(source);
    }

    private WebKnowledgeSourceView submit(WebKnowledgeSourceEntity source, Instant now) {
        String revisionId = "wrev-" + UUID.randomUUID();
        WebKnowledgeRevisionEntity revision = revisions.save(
                new WebKnowledgeRevisionEntity(revisionId, source.sourceId(), now));
        source.status("PENDING", now);
        sources.save(source);

        RagIndexJob job = jobs.createJob(
                new RagIndexJobCreateRequest(
                        SOURCE_TYPE,
                        source.sourceId(),
                        revisionId,
                        SOURCE_TYPE,
                        true,
                        null,
                        source.displayName()),
                new RagIndexJobSourceRequest(
                        java.util.Map.of(
                                "workspaceId", source.workspaceId(),
                                "sourceRevisionId", revisionId),
                        List.of(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        false,
                        source.embeddingDeploymentId()));
        revision.job(job.jobId(), now);
        revisions.save(revision);
        executeAfterCommit(
                () -> jobs.startJob(job.jobId()),
                source.sourceId(),
                revision.revisionId());
        return view(source, revision);
    }

    private WebKnowledgeSourceView submitSite(
            WebKnowledgeSourceEntity source,
            ResolvedWebCrawlPolicy policy,
            String principal,
            Instant now) {
        WebKnowledgeCrawlRunEntity run = siteCrawlCoordinator.createRun(source, policy, principal, null);
        source.status("PENDING", now);
        sources.save(source);
        executeAfterCommit(
                () -> siteCrawlCoordinator.execute(run.runId()),
                source.sourceId(),
                run.runId());
        return view(source, null);
    }

    private void executeAfterCommit(Runnable task, String sourceId, String revisionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(task, sourceId, revisionId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(task, sourceId, revisionId);
            }
        });
    }

    private void dispatch(Runnable task, String sourceId, String revisionId) {
        try {
            executor.execute(task);
        } catch (RuntimeException ex) {
            Instant now = Instant.now();
            revisions.findByRevisionIdAndSourceId(revisionId, sourceId).ifPresent(revision -> {
                revision.fail("WEB_SOURCE_EXECUTOR_REJECTED", now);
                revisions.save(revision);
            });
            sources.findById(sourceId).ifPresent(source -> {
                source.status("FAILED", now);
                sources.save(source);
            });
        }
    }

    private boolean hasActiveRevision(String sourceId) {
        return revisions.findBySourceIdOrderByCreatedAtDesc(sourceId).stream()
                .findFirst()
                .map(this::active)
                .orElse(false);
    }

    private boolean active(WebKnowledgeRevisionEntity revision) {
        if (!active(revision.status())) {
            return false;
        }
        if (revision.ragJobId() == null) {
            return true;
        }
        return jobs.getJob(revision.ragJobId())
                .map(job -> active(job.status()))
                .orElse(true);
    }

    private boolean active(String status) {
        return "PENDING".equals(status)
                || "FETCHING".equals(status)
                || "NORMALIZING".equals(status)
                || "INDEXING".equals(status);
    }

    private boolean active(RagIndexJobStatus status) {
        return status == RagIndexJobStatus.PENDING || status == RagIndexJobStatus.RUNNING;
    }

    private WebKnowledgeSourceEntity requireSource(Long workspaceId, String sourceId) {
        return sources.findBySourceIdAndWorkspaceIdAndArchivedFalse(sourceId, workspaceId)
                .orElseThrow(() -> new NoSuchElementException("WEB_SOURCE_NOT_FOUND"));
    }

    private WebKnowledgeRevisionEntity latestRevision(String sourceId) {
        return revisions.findBySourceIdOrderByCreatedAtDesc(sourceId).stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("WEB_SOURCE_REVISION_NOT_FOUND"));
    }

    private WebKnowledgeRevisionEntity currentRevision(WebKnowledgeSourceEntity source) {
        if (source.currentRevisionId() != null) {
            return revisions.findByRevisionIdAndSourceId(source.currentRevisionId(), source.sourceId())
                    .orElse(null);
        }
        return revisions.findBySourceIdOrderByCreatedAtDesc(source.sourceId()).stream().findFirst().orElse(null);
    }

    private WebKnowledgeSourceView view(
            WebKnowledgeSourceEntity source,
            WebKnowledgeRevisionEntity revision) {
        WebKnowledgeRevisionEntity latest = revisions.findBySourceIdOrderByCreatedAtDesc(source.sourceId())
                .stream()
                .findFirst()
                .orElse(revision);
        WebKnowledgeRevisionEntity content = revision == null ? latest : revision;
        WebKnowledgeCrawlRunEntity run = siteCrawlCoordinator == null
                ? null
                : siteCrawlCoordinator.latestRun(source.workspaceId(), source.sourceId()).orElse(null);
        return new WebKnowledgeSourceView(
                source.sourceId(),
                source.workspaceId(),
                source.normalizedUrl(),
                source.canonicalUrl(),
                source.host(),
                source.displayName(),
                source.embeddingDeploymentId(),
                source.embeddingSpaceId(),
                source.status(),
                source.collectionMode(),
                source.currentRevisionId(),
                source.currentCorpusRevisionId(),
                latest == null ? null : latest.status(),
                run == null ? null : run.runId(),
                run != null && run.truncated(),
                run == null ? 0 : run.discoveredCount(),
                run == null ? 0 : run.fetchedCount(),
                run == null ? 0 : run.indexedCount(),
                run == null ? 0 : run.unchangedCount(),
                run == null ? 0 : run.updatedCount(),
                run == null ? 0 : run.removedCount(),
                run == null ? 0 : run.failedCount(),
                run == null ? 0 : run.skippedCount(),
                content == null ? null : content.title(),
                content == null ? null : content.publisher(),
                content == null ? null : content.language(),
                content == null ? null : content.publishedAt(),
                content == null ? null : content.modifiedAt(),
                content == null ? null : content.retrievedAt(),
                content == null ? null : content.contentPreview(),
                run != null && run.errorCode() != null
                        ? run.errorCode()
                        : latest == null ? null : latest.errorCode(),
                source.createdAt(),
                source.updatedAt());
    }

    private void assertCollectionModeAvailable(WebKnowledgeCollectionMode mode) {
        if (mode == WebKnowledgeCollectionMode.SITE
                && (!siteCrawlEnabled || siteCrawlCoordinator == null)) {
            throw new IllegalStateException("WEB_SITE_CRAWL_DISABLED");
        }
    }

    private ResolvedWebCrawlPolicy readPolicy(String json) {
        if (json == null || json.isBlank()) {
            return crawlPolicyResolver.resolve(WebKnowledgeCollectionMode.SITE, WebCrawlPolicyInput.defaults());
        }
        try {
            return objectMapper.readValue(json, ResolvedWebCrawlPolicy.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("WEB_CRAWL_POLICY_INVALID", ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("WEB_CRAWL_POLICY_SERIALIZATION_FAILED", ex);
        }
    }

    private static String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
