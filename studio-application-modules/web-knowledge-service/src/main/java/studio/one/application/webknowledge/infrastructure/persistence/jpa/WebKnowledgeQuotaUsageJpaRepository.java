package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebKnowledgeQuotaUsageJpaRepository
        extends JpaRepository<WebKnowledgeQuotaUsageEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quota from WebKnowledgeQuotaUsageEntity quota where quota.workspaceId = :workspaceId")
    Optional<WebKnowledgeQuotaUsageEntity> findForUpdate(@Param("workspaceId") Long workspaceId);
}
