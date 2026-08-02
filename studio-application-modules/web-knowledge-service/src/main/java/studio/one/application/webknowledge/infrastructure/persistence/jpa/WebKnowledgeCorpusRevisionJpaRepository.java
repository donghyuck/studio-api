package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebKnowledgeCorpusRevisionJpaRepository
        extends JpaRepository<WebKnowledgeCorpusRevisionEntity, String> {

    Optional<WebKnowledgeCorpusRevisionEntity> findByCorpusRevisionIdAndWorkspaceIdAndSourceIdAndStatus(
            String corpusRevisionId, Long workspaceId, String sourceId, String status);

    Optional<WebKnowledgeCorpusRevisionEntity> findFirstBySourceIdAndWorkspaceIdAndStatusOrderByCreatedAtDesc(
            String sourceId, Long workspaceId, String status);

    List<WebKnowledgeCorpusRevisionEntity> findBySourceIdAndWorkspaceIdOrderByCreatedAtDesc(
            String sourceId, Long workspaceId);
}
