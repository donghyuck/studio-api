package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebKnowledgeSourceJpaRepository extends JpaRepository<WebKnowledgeSourceEntity, String> {

    Optional<WebKnowledgeSourceEntity> findByWorkspaceIdAndNormalizedUrlHashAndEmbeddingDeploymentIdAndArchivedFalse(
            Long workspaceId, String normalizedUrlHash, String embeddingDeploymentId);

    Optional<WebKnowledgeSourceEntity> findBySourceIdAndWorkspaceIdAndArchivedFalse(String sourceId, Long workspaceId);

    List<WebKnowledgeSourceEntity> findByWorkspaceIdAndArchivedFalseOrderByUpdatedAtDesc(Long workspaceId);

    long countByWorkspaceIdAndArchivedFalse(Long workspaceId);
}
