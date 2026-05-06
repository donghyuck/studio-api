package studio.one.application.wiki.web.dto;

import java.time.Instant;

import studio.one.application.wiki.model.WikiRevisionSummary;

public record WikiRevisionSummaryDto(
        Long revisionId,
        Long pageId,
        Long workspaceId,
        String slug,
        String title,
        int revisionNo,
        Long createdBy,
        Instant createdAt) {

    public static WikiRevisionSummaryDto from(WikiRevisionSummary revision) {
        return new WikiRevisionSummaryDto(
                revision.revisionId(),
                revision.pageId(),
                revision.workspaceId(),
                revision.slug(),
                revision.title(),
                revision.revisionNo(),
                revision.createdBy(),
                revision.createdAt());
    }
}
