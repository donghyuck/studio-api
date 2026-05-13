package studio.one.application.wiki.domain.model;

import java.time.Instant;

public class WikiRevisionSummary {
    private final Long revisionId;
    private final Long pageId;
    private final Long workspaceId;
    private final String slug;
    private final String title;
    private final int revisionNo;
    private final Long createdBy;
    private final Instant createdAt;

    public WikiRevisionSummary(
            Long revisionId,
            Long pageId,
            Long workspaceId,
            String slug,
            String title,
            int revisionNo,
            Long createdBy,
            Instant createdAt) {
        this.revisionId = revisionId;
        this.pageId = pageId;
        this.workspaceId = workspaceId;
        this.slug = slug;
        this.title = title;
        this.revisionNo = revisionNo;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long revisionId() {
        return revisionId;
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

    public int revisionNo() {
        return revisionNo;
    }

    public Long createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

}
