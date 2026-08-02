package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebKnowledgePageJpaRepository extends JpaRepository<WebKnowledgePageEntity, String> {

    Optional<WebKnowledgePageEntity> findByWorkspaceIdAndSourceIdAndNormalizedUrlHash(
            Long workspaceId, String sourceId, String normalizedUrlHash);

    Optional<WebKnowledgePageEntity> findFirstByWorkspaceIdAndSourceIdAndCanonicalUrlHashAndActiveTrue(
            Long workspaceId, String sourceId, String canonicalUrlHash);

    Optional<WebKnowledgePageEntity> findByPageIdAndWorkspaceIdAndSourceId(
            String pageId, Long workspaceId, String sourceId);

    List<WebKnowledgePageEntity> findByWorkspaceIdAndSourceIdOrderByNormalizedUrlAsc(
            Long workspaceId, String sourceId);

    List<WebKnowledgePageEntity> findByWorkspaceIdAndSourceIdAndActiveTrueOrderByNormalizedUrlAsc(
            Long workspaceId, String sourceId);

    @Query("""
            select
                page.normalizedUrl as normalizedUrl,
                page.canonicalUrl as canonicalUrl,
                page.active as active,
                page.missingRunCount as missingRunCount,
                page.firstSeenAt as firstSeenAt,
                page.lastSeenAt as lastSeenAt,
                page.updatedAt as updatedAt,
                revision.title as title
            from WebKnowledgePageEntity page
            left join WebKnowledgePageRevisionEntity revision
              on revision.pageRevisionId = page.currentPageRevisionId
            where page.workspaceId = :workspaceId
              and page.sourceId = :sourceId
            order by page.normalizedUrl asc
            """)
    List<PageSummary> findSummaries(
            @Param("workspaceId") Long workspaceId,
            @Param("sourceId") String sourceId);

    long countByWorkspaceIdAndActiveTrue(Long workspaceId);

    interface PageSummary {
        String getNormalizedUrl();
        String getCanonicalUrl();
        boolean getActive();
        int getMissingRunCount();
        java.time.Instant getFirstSeenAt();
        java.time.Instant getLastSeenAt();
        java.time.Instant getUpdatedAt();
        String getTitle();
    }
}
