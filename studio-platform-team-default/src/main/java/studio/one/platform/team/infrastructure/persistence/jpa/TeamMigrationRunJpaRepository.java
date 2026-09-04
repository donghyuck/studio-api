package studio.one.platform.team.infrastructure.persistence.jpa;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMigrationRunJpaRepository extends JpaRepository<TeamMigrationRunEntity, String> {
    Optional<TeamMigrationRunEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TeamMigrationRunEntity r where r.runId = :runId")
    Optional<TeamMigrationRunEntity> findForUpdate(@Param("runId") String runId);
}
