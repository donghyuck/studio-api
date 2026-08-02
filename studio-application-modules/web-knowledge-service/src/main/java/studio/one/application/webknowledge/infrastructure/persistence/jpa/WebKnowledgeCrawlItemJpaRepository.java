package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebKnowledgeCrawlItemJpaRepository
        extends JpaRepository<WebKnowledgeCrawlItemEntity, String> {

    Optional<WebKnowledgeCrawlItemEntity> findByRunIdAndNormalizedUrlHash(
            String runId, String normalizedUrlHash);

    List<WebKnowledgeCrawlItemEntity> findByRunIdOrderByDiscoveryOrder(String runId);

    List<WebKnowledgeCrawlItemEntity> findByRunIdAndStatusOrderByDiscoveryOrder(
            String runId, String status);
}
