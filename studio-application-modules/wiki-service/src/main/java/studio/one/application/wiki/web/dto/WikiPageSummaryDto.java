package studio.one.application.wiki.web.dto;

import java.time.Instant;

import studio.one.application.wiki.model.WikiPageSummary;

public record WikiPageSummaryDto(
        Long pageId,
        Long workspaceId,
        String slug,
        String title,
        Long currentRevisionId,
        int revisionNo,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public static WikiPageSummaryDto from(WikiPageSummary page) {
        return new WikiPageSummaryDto(
                page.pageId(),
                page.workspaceId(),
                page.slug(),
                page.title(),
                page.currentRevisionId(),
                page.revisionNo(),
                page.archived(),
                page.createdAt(),
                page.updatedAt());
    }
}
