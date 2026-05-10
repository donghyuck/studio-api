package studio.one.application.wiki.domain.model;

import java.time.Instant;

public record WikiRevisionSummary(
        Long revisionId,
        Long pageId,
        Long workspaceId,
        String slug,
        String title,
        int revisionNo,
        Long createdBy,
        Instant createdAt) {
}
