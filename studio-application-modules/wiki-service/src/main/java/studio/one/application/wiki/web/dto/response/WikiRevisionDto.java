package studio.one.application.wiki.web.dto.response;

import java.time.Instant;

import studio.one.application.wiki.domain.model.WikiRevision;

public class WikiRevisionDto {

    private final Long revisionId;
    private final Long pageId;
    private final Long workspaceId;
    private final String slug;
    private final String title;
    private final String markdown;
    private final String sanitizedHtml;
    private final int revisionNo;
    private final Long createdBy;
    private final Instant createdAt;

    public WikiRevisionDto(
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
        this.revisionId = revisionId;
        this.pageId = pageId;
        this.workspaceId = workspaceId;
        this.slug = slug;
        this.title = title;
        this.markdown = markdown;
        this.sanitizedHtml = sanitizedHtml;
        this.revisionNo = revisionNo;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long revisionId() { return revisionId; }

    public Long pageId() { return pageId; }

    public Long workspaceId() { return workspaceId; }

    public String slug() { return slug; }

    public String title() { return title; }

    public String markdown() { return markdown; }

    public String sanitizedHtml() { return sanitizedHtml; }

    public int revisionNo() { return revisionNo; }

    public Long createdBy() { return createdBy; }

    public Instant createdAt() { return createdAt; }

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
