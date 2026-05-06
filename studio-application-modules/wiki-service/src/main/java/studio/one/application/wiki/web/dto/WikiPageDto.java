package studio.one.application.wiki.web.dto;

import java.time.Instant;

import studio.one.application.wiki.model.WikiPage;

public record WikiPageDto(
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

    public static WikiPageDto from(WikiPage page) {
        return new WikiPageDto(
                page.pageId(),
                page.workspaceId(),
                page.slug(),
                page.title(),
                page.markdown(),
                page.sanitizedHtml(),
                page.currentRevisionId(),
                page.revisionNo(),
                page.archived(),
                page.createdAt(),
                page.updatedAt());
    }
}
