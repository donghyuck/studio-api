package studio.one.application.webknowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeQuotaUsageEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeQuotaUsageJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;

class WebKnowledgeQuotaServiceTest {

    @Test
    void reservesOnlyWhenObservedAndReservedUsageFitsWorkspaceLimits() {
        Fixture fixture = fixture(new WebKnowledgeQuotaService.Limits(3, 10, 1_000));
        when(fixture.sources.countByWorkspaceIdAndArchivedFalse(2L)).thenReturn(1L);
        when(fixture.pages.countByWorkspaceIdAndActiveTrue(2L)).thenReturn(4L);
        when(fixture.revisions.sumCompletedSnapshotUnitsByWorkspaceId(2L)).thenReturn(400L);

        fixture.service.reserve(2L, 5, 500);

        assertEquals(5, fixture.quota.reservedPageCount());
        assertEquals(500, fixture.quota.reservedSnapshotBytes());
        verify(fixture.quotas, times(2)).save(fixture.quota);
    }

    @Test
    void rejectsReservationBeforeCreatingAJobWhenWorkspaceQuotaWouldBeExceeded() {
        Fixture fixture = fixture(new WebKnowledgeQuotaService.Limits(3, 10, 1_000));
        when(fixture.sources.countByWorkspaceIdAndArchivedFalse(2L)).thenReturn(1L);
        when(fixture.pages.countByWorkspaceIdAndActiveTrue(2L)).thenReturn(6L);
        when(fixture.revisions.sumCompletedSnapshotUnitsByWorkspaceId(2L)).thenReturn(400L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> fixture.service.reserve(2L, 5, 500));

        assertEquals("WEB_CRAWL_QUOTA_EXCEEDED", error.getMessage());
        assertEquals(0, fixture.quota.reservedPageCount());
        assertEquals(0, fixture.quota.reservedSnapshotBytes());
    }

    @Test
    void releasesRunReservationExactlyOnce() {
        Fixture fixture = fixture(new WebKnowledgeQuotaService.Limits(3, 10, 1_000));
        fixture.quota.reserve(5, 500, Instant.now());
        WebKnowledgeCrawlRunEntity run = new WebKnowledgeCrawlRunEntity(
                "wrun-1", 2L, "wsrc-1", null, "{}", "policy-hash", "tester", Instant.now());
        run.quotaReservation(5, 500, Instant.now());
        when(fixture.runs.findForUpdate("wrun-1")).thenReturn(Optional.of(run));
        when(fixture.sources.countByWorkspaceIdAndArchivedFalse(2L)).thenReturn(1L);
        when(fixture.pages.countByWorkspaceIdAndActiveTrue(2L)).thenReturn(4L);
        when(fixture.revisions.sumCompletedSnapshotUnitsByWorkspaceId(2L)).thenReturn(400L);

        fixture.service.releaseReservation("wrun-1");
        fixture.service.releaseReservation("wrun-1");

        assertEquals(0, fixture.quota.reservedPageCount());
        assertEquals(0, fixture.quota.reservedSnapshotBytes());
        assertNotNull(run.quotaReleasedAt());
        verify(fixture.runs).save(run);
    }

    @Test
    void rejectsNewSourceAtSourceQuota() {
        Fixture fixture = fixture(new WebKnowledgeQuotaService.Limits(1, 10, 1_000));
        when(fixture.sources.countByWorkspaceIdAndArchivedFalse(2L)).thenReturn(1L);
        when(fixture.pages.countByWorkspaceIdAndActiveTrue(2L)).thenReturn(0L);
        when(fixture.revisions.sumCompletedSnapshotUnitsByWorkspaceId(2L)).thenReturn(0L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> fixture.service.assertSourceCapacity(2L));

        assertEquals("WEB_CRAWL_QUOTA_EXCEEDED", error.getMessage());
        verify(fixture.runs, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Fixture fixture(WebKnowledgeQuotaService.Limits limits) {
        WebKnowledgeQuotaUsageJpaRepository quotas = mock(WebKnowledgeQuotaUsageJpaRepository.class);
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        WebKnowledgePageJpaRepository pages = mock(WebKnowledgePageJpaRepository.class);
        WebKnowledgePageRevisionJpaRepository revisions = mock(WebKnowledgePageRevisionJpaRepository.class);
        WebKnowledgeCrawlRunJpaRepository runs = mock(WebKnowledgeCrawlRunJpaRepository.class);
        WebKnowledgeQuotaUsageEntity quota = new WebKnowledgeQuotaUsageEntity(2L, Instant.now());
        when(quotas.findForUpdate(2L)).thenReturn(Optional.of(quota));
        return new Fixture(
                quotas,
                sources,
                pages,
                revisions,
                runs,
                quota,
                new WebKnowledgeQuotaService(quotas, sources, pages, revisions, runs, limits));
    }

    private record Fixture(
            WebKnowledgeQuotaUsageJpaRepository quotas,
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgePageJpaRepository pages,
            WebKnowledgePageRevisionJpaRepository revisions,
            WebKnowledgeCrawlRunJpaRepository runs,
            WebKnowledgeQuotaUsageEntity quota,
            WebKnowledgeQuotaService service) {
    }
}
