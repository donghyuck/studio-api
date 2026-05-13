package studio.one.application.wiki.web.dto.response;

import java.time.Instant;

import studio.one.application.wiki.domain.model.WikiPageSummary;

public class WikiPageSummaryDto {

    private final Long pageId;
    private final Long workspaceId;
    private final String slug;
    private final String title;
    private final Long currentRevisionId;
    private final int revisionNo;
    private final boolean archived;
    private final Instant createdAt;
    private final Instant updatedAt;

    public WikiPageSummaryDto(
            Long pageId,
            Long workspaceId,
            String slug,
            String title,
            Long currentRevisionId,
            int revisionNo,
            boolean archived,
            Instant createdAt,
            Instant updatedAt) {
        this.pageId = pageId;
        this.workspaceId = workspaceId;
        this.slug = slug;
        this.title = title;
        this.currentRevisionId = currentRevisionId;
        this.revisionNo = revisionNo;
        this.archived = archived;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long pageId() { return pageId; }

    public Long workspaceId() { return workspaceId; }

    public String slug() { return slug; }

    public String title() { return title; }

    public Long currentRevisionId() { return currentRevisionId; }

    public int revisionNo() { return revisionNo; }

    public boolean archived() { return archived; }

    public Instant createdAt() { return createdAt; }

    public Instant updatedAt() { return updatedAt; }

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
