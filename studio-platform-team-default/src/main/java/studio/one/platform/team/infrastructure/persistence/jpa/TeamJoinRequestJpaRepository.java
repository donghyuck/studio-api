package studio.one.platform.team.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.platform.team.domain.model.TeamJoinRequestStatus;

public interface TeamJoinRequestJpaRepository extends JpaRepository<TeamJoinRequestEntity, Long> {
    Optional<TeamJoinRequestEntity> findByTeamIdAndUserId(Long teamId, Long userId);
    List<TeamJoinRequestEntity> findByTeamIdOrderByRequestedAtAsc(Long teamId);
    List<TeamJoinRequestEntity> findByTeamIdAndStatusOrderByRequestedAtAsc(
            Long teamId,
            TeamJoinRequestStatus status);
}
