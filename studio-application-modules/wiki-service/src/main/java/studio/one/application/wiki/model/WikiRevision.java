package studio.one.application.wiki.model;

import java.time.Instant;

public record WikiRevision(
        Long revisionId,
        Long pageId,
        Long workspaceId,
        String slug,
        String title,
        String markdown,
        String sanitizedHtml,
        int revisionNo,
        Long createdBy,
        Instant createdAt) {
}
