package studio.one.application.webknowledge.application;

import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeQuotaUsageEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeQuotaUsageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunJpaRepository;

public class WebKnowledgeQuotaService {

    private final WebKnowledgeQuotaUsageJpaRepository quotas;
    private final WebKnowledgeSourceJpaRepository sources;
    private final WebKnowledgePageJpaRepository pages;
    private final WebKnowledgePageRevisionJpaRepository revisions;
    private final WebKnowledgeCrawlRunJpaRepository runs;
    private final Limits limits;

    public WebKnowledgeQuotaService(
            WebKnowledgeQuotaUsageJpaRepository quotas,
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgePageJpaRepository pages,
            WebKnowledgePageRevisionJpaRepository revisions,
            WebKnowledgeCrawlRunJpaRepository runs,
            Limits limits) {
        this.quotas = quotas;
        this.sources = sources;
        this.pages = pages;
        this.revisions = revisions;
        this.runs = runs;
        this.limits = limits == null ? Limits.defaults() : limits.validated();
    }

    @Transactional
    public void assertSourceCapacity(Long workspaceId) {
        WebKnowledgeQuotaUsageEntity quota = locked(workspaceId);
        Usage usage = observe(workspaceId, quota);
        if (usage.sources() >= limits.maxSourcesPerWorkspace()) {
            throw new IllegalStateException("WEB_CRAWL_QUOTA_EXCEEDED");
        }
    }

    @Transactional
    public void reserve(Long workspaceId, long requestedPages, long requestedSnapshotUnits) {
        WebKnowledgeQuotaUsageEntity quota = locked(workspaceId);
        Usage usage = observe(workspaceId, quota);
        if (usage.pages() + quota.reservedPageCount() + requestedPages
                        > limits.maxActivePagesPerWorkspace()
                || usage.snapshotUnits() + quota.reservedSnapshotBytes() + requestedSnapshotUnits
                        > limits.maxSnapshotUnitsPerWorkspace()) {
            throw new IllegalStateException("WEB_CRAWL_QUOTA_EXCEEDED");
        }
        quota.reserve(requestedPages, requestedSnapshotUnits, Instant.now());
        quotas.save(quota);
    }

    @Transactional
    public void releaseReservation(String runId) {
        var run = runs.findForUpdate(runId).orElse(null);
        if (run == null || run.quotaReleasedAt() != null) {
            return;
        }
        WebKnowledgeQuotaUsageEntity quota = locked(run.workspaceId());
        observe(run.workspaceId(), quota);
        quota.release(run.reservedPageCount(), run.reservedSnapshotBytes(), Instant.now());
        quotas.save(quota);
        run.quotaReleased(Instant.now());
        runs.save(run);
    }

    private WebKnowledgeQuotaUsageEntity locked(Long workspaceId) {
        return quotas.findForUpdate(workspaceId).orElseGet(() ->
                quotas.saveAndFlush(new WebKnowledgeQuotaUsageEntity(workspaceId, Instant.now())));
    }

    private Usage observe(Long workspaceId, WebKnowledgeQuotaUsageEntity quota) {
        Usage usage = new Usage(
                sources.countByWorkspaceIdAndArchivedFalse(workspaceId),
                pages.countByWorkspaceIdAndActiveTrue(workspaceId),
                revisions.sumCompletedSnapshotUnitsByWorkspaceId(workspaceId));
        quota.observed(usage.sources(), usage.pages(), usage.snapshotUnits(), Instant.now());
        quotas.save(quota);
        return usage;
    }

    public record Limits(
            long maxSourcesPerWorkspace,
            long maxActivePagesPerWorkspace,
            long maxSnapshotUnitsPerWorkspace) {

        public Limits validated() {
            if (maxSourcesPerWorkspace < 1
                    || maxActivePagesPerWorkspace < 1
                    || maxSnapshotUnitsPerWorkspace < 1) {
                throw new IllegalArgumentException("WEB_CRAWL_QUOTA_LIMITS_INVALID");
            }
            return this;
        }

        public static Limits defaults() {
            return new Limits(100, 10_000, 1_000_000_000L);
        }
    }

    private record Usage(long sources, long pages, long snapshotUnits) {
    }
}
