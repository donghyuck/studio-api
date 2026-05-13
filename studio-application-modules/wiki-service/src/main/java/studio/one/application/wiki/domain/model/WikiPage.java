package studio.one.application.wiki.domain.model;

import java.time.Instant;

public class WikiPage {
    private final Long pageId;
    private final Long workspaceId;
    private final String slug;
    private final String title;
    private final String markdown;
    private final String sanitizedHtml;
    private final Long currentRevisionId;
    private final int revisionNo;
    private final boolean archived;
    private final Instant createdAt;
    private final Instant updatedAt;

    public WikiPage(
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
        this.pageId = pageId;
        this.workspaceId = workspaceId;
        this.slug = slug;
        this.title = title;
        this.markdown = markdown;
        this.sanitizedHtml = sanitizedHtml;
        this.currentRevisionId = currentRevisionId;
        this.revisionNo = revisionNo;
        this.archived = archived;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long pageId() {
        return pageId;
    }

    public Long workspaceId() {
        return workspaceId;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }

    public String markdown() {
        return markdown;
    }

    public String sanitizedHtml() {
        return sanitizedHtml;
    }

    public Long currentRevisionId() {
        return currentRevisionId;
    }

    public int revisionNo() {
        return revisionNo;
    }

    public boolean archived() {
        return archived;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

}
