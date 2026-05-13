package studio.one.application.wiki.web.dto.response;

import java.time.Instant;

import studio.one.application.wiki.domain.model.WikiRevisionSummary;

public class WikiRevisionSummaryDto {

    private final Long revisionId;
    private final Long pageId;
    private final Long workspaceId;
    private final String slug;
    private final String title;
    private final int revisionNo;
    private final Long createdBy;
    private final Instant createdAt;

    public WikiRevisionSummaryDto(
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

    public Long revisionId() { return revisionId; }

    public Long pageId() { return pageId; }

    public Long workspaceId() { return workspaceId; }

    public String slug() { return slug; }

    public String title() { return title; }

    public int revisionNo() { return revisionNo; }

    public Long createdBy() { return createdBy; }

    public Instant createdAt() { return createdAt; }

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
