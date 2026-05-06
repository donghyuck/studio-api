package studio.one.application.wiki.web.dto;

import java.time.Instant;

import studio.one.application.wiki.model.WikiRevision;

public record WikiRevisionDto(
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

    public static WikiRevisionDto from(WikiRevision revision) {
        return new WikiRevisionDto(
                revision.revisionId(),
                revision.pageId(),
                revision.workspaceId(),
                revision.slug(),
                revision.title(),
                revision.markdown(),
                revision.sanitizedHtml(),
                revision.revisionNo(),
                revision.createdBy(),
                revision.createdAt());
    }
}
