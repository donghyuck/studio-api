package studio.one.platform.team.domain.model;

import java.time.Instant;

public record TeamRef(
        Long teamId,
        Long companyId,
        String name,
        String slug,
        String description,
        TeamVisibility visibility,
        TeamJoinPolicy joinPolicy,
        TeamStatus status,
        boolean ragEnabled,
        TeamRagReplyMode ragReplyMode,
        long permissionVersion,
        Instant createdAt,
        Instant updatedAt) {

    public boolean archived() {
        return status == TeamStatus.ARCHIVED;
    }
}
