package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebKnowledgePageRevisionJpaRepository
        extends JpaRepository<WebKnowledgePageRevisionEntity, String> {

    Optional<WebKnowledgePageRevisionEntity> findByPageRevisionIdAndWorkspaceIdAndSourceId(
            String pageRevisionId, Long workspaceId, String sourceId);

    Optional<WebKnowledgePageRevisionEntity> findFirstByPageIdOrderByCreatedAtDesc(String pageId);

    Optional<WebKnowledgePageRevisionEntity>
            findFirstByWorkspaceIdAndSourceIdAndContentHashAndStatusOrderByCreatedAtAsc(
                    Long workspaceId, String sourceId, String contentHash, String status);

    List<WebKnowledgePageRevisionEntity> findByRunIdOrderByCreatedAtAsc(String runId);

    @Query(value = """
            select coalesce(sum(char_length(normalized_snapshot)), 0)
            from web_knowledge_page_revision
            where workspace_id = :workspaceId
              and status = 'COMPLETED'
            """, nativeQuery = true)
    long sumCompletedSnapshotUnitsByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
