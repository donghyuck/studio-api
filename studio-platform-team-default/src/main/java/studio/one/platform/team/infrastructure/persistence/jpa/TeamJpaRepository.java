package studio.one.platform.team.infrastructure.persistence.jpa;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamJpaRepository extends JpaRepository<TeamEntity, Long>, JpaSpecificationExecutor<TeamEntity> {
    boolean existsBySlugIgnoreCase(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TeamEntity t where t.teamId = :teamId")
    Optional<TeamEntity> findForUpdate(@Param("teamId") Long teamId);
}
