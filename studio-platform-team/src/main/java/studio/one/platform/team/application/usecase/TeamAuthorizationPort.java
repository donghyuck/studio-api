package studio.one.platform.team.application.usecase;

import java.util.Optional;
import java.util.Set;

import studio.one.platform.team.domain.model.TeamRole;

public interface TeamAuthorizationPort {
    boolean isMember(Long teamId, Long userId);
    Optional<TeamRole> findEffectiveRole(Long teamId, Long userId);
    Set<String> grantedActions(Long teamId, Long userId);
    void assertGranted(Long teamId, Long userId, String action);
    long permissionVersion(Long teamId);
}
