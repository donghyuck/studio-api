package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebKnowledgeRevisionJpaRepository extends JpaRepository<WebKnowledgeRevisionEntity, String> {

    Optional<WebKnowledgeRevisionEntity> findByRevisionIdAndSourceId(String revisionId, String sourceId);

    List<WebKnowledgeRevisionEntity> findBySourceIdOrderByCreatedAtDesc(String sourceId);
}
