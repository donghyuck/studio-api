package studio.one.application.wiki.domain.model;

import java.time.Instant;

public record WikiPage(
        Long pageId,
        Long workspaceId,
        String slug,
        String title,
        String markdown,
        String sanitizedHtml,
        Long currentRevisionId,
        int revisionNo,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {
}
