package studio.one.platform.team.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMigrationLockJpaRepository extends JpaRepository<TeamMigrationLockEntity, String> {
    List<TeamMigrationLockEntity> findByRunId(String runId);
    void deleteByRunId(String runId);
}
