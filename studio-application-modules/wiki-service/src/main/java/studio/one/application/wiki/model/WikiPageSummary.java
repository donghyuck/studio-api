package studio.one.application.wiki.model;

import java.time.Instant;

public record WikiPageSummary(
        Long pageId,
        Long workspaceId,
        String slug,
        String title,
        Long currentRevisionId,
        int revisionNo,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {
}
