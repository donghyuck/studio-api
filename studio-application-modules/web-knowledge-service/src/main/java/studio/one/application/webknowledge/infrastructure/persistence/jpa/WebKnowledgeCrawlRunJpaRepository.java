package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebKnowledgeCrawlRunJpaRepository
        extends JpaRepository<WebKnowledgeCrawlRunEntity, String> {

    Optional<WebKnowledgeCrawlRunEntity> findByRunIdAndSourceIdAndWorkspaceId(
            String runId, String sourceId, Long workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from WebKnowledgeCrawlRunEntity run where run.runId = :runId")
    Optional<WebKnowledgeCrawlRunEntity> findForUpdate(@Param("runId") String runId);

    Optional<WebKnowledgeCrawlRunEntity> findFirstBySourceIdAndWorkspaceIdOrderByCreatedAtDesc(
            String sourceId, Long workspaceId);

    List<WebKnowledgeCrawlRunEntity> findBySourceIdAndWorkspaceIdOrderByCreatedAtDesc(
            String sourceId, Long workspaceId);

    List<WebKnowledgeCrawlRunEntity> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    long countByStatusIn(Collection<String> statuses);

    long countByWorkspaceIdAndStatusIn(Long workspaceId, Collection<String> statuses);

    long countByRequestedByAndStatusIn(String requestedBy, Collection<String> statuses);
}
