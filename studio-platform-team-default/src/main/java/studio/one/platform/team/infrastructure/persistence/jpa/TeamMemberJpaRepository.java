package studio.one.platform.team.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamRole;

public interface TeamMemberJpaRepository extends JpaRepository<TeamMemberEntity, Long> {
    Optional<TeamMemberEntity> findByTeamIdAndUserId(Long teamId, Long userId);
    Optional<TeamMemberEntity> findByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamMemberStatus status);
    boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamMemberStatus status);
    List<TeamMemberEntity> findByTeamIdAndStatusOrderByUserIdAsc(Long teamId, TeamMemberStatus status);
    long countByTeamIdAndRoleAndStatus(Long teamId, TeamRole role, TeamMemberStatus status);
}
