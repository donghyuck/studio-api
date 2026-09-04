package studio.one.platform.team.domain.model;

import java.time.Instant;

public record TeamJoinRequestRef(
        Long requestId,
        Long teamId,
        Long userId,
        TeamJoinRequestStatus status,
        Instant requestedAt,
        Instant resolvedAt,
        Long resolvedBy) {
}
