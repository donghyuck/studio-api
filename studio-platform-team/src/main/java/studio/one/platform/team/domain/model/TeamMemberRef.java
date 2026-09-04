package studio.one.platform.team.domain.model;

import java.time.Instant;

public record TeamMemberRef(
        Long teamId,
        Long userId,
        TeamRole role,
        TeamMemberStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
