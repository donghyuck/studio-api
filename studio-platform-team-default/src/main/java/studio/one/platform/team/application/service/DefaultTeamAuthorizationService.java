package studio.one.platform.team.application.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.platform.team.application.error.TeamNotFoundException;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.team.domain.model.TeamRole;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberJpaRepository;

@RequiredArgsConstructor
public class DefaultTeamAuthorizationService implements TeamAuthorizationPort {
    private final TeamJpaRepository teamRepository;
    private final TeamMemberJpaRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(Long teamId, Long userId) {
        return validId(teamId) && validId(userId)
                && memberRepository.existsByTeamIdAndUserIdAndStatus(teamId, userId, TeamMemberStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TeamRole> findEffectiveRole(Long teamId, Long userId) {
        if (!validId(teamId) || !validId(userId)) {
            return Optional.empty();
        }
        return memberRepository.findByTeamIdAndUserIdAndStatus(teamId, userId, TeamMemberStatus.ACTIVE)
                .map(member -> member.getRole());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> grantedActions(Long teamId, Long userId) {
        TeamEntity team = team(teamId);
        if (team.toRef().archived()) {
            return Set.of(TeamPermissionActions.READ, TeamPermissionActions.MEMBER_READ).stream()
                    .filter(TeamPermissionActions.grantedActions(findEffectiveRole(teamId, userId).orElse(null))::contains)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return TeamPermissionActions.grantedActions(findEffectiveRole(teamId, userId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public void assertGranted(Long teamId, Long userId, String action) {
        if (!StringUtils.hasText(action) || !grantedActions(teamId, userId).contains(action)) {
            throw new AccessDeniedException("Team permission denied: " + action);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long permissionVersion(Long teamId) {
        return team(teamId).getPermissionVersion();
    }

    private TeamEntity team(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));
    }

    private boolean validId(Long value) {
        return value != null && value > 0;
    }
}
