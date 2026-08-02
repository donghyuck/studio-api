package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "web_knowledge_page_revision")
public class WebKnowledgePageRevisionEntity {

    @Id
    @Column(name = "page_revision_id", length = 80, nullable = false)
    private String pageRevisionId;
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "source_id", length = 80, nullable = false)
    private String sourceId;
    @Column(name = "page_id", length = 80, nullable = false)
    private String pageId;
    @Column(name = "run_id", length = 80, nullable = false)
    private String runId;
    @Column(name = "status", length = 32, nullable = false)
    private String status;
    @Column(name = "title", length = 500)
    private String title;
    @Column(name = "publisher", length = 300)
    private String publisher;
    @Column(name = "language_code", length = 32)
    private String language;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "source_modified_at")
    private Instant modifiedAt;
    @Column(name = "retrieved_at")
    private Instant retrievedAt;
    @Column(name = "etag", length = 500)
    private String etag;
    @Column(name = "last_modified", length = 500)
    private String lastModified;
    @Column(name = "content_type", length = 160)
    private String contentType;
    @Column(name = "content_length")
    private Long contentLength;
    @Column(name = "content_hash", length = 64)
    private String contentHash;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "normalized_snapshot", length = Length.LONG32)
    private String normalizedSnapshot;
    @Column(name = "content_preview", length = 500)
    private String contentPreview;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "metadata_json", length = Length.LONG32)
    private String metadataJson;
    @Column(name = "error_code", length = 80)
    private String errorCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected WebKnowledgePageRevisionEntity() {
    }

    public WebKnowledgePageRevisionEntity(
            String pageRevisionId,
            Long workspaceId,
            String sourceId,
            String pageId,
            String runId,
            Instant now) {
        this.pageRevisionId = pageRevisionId;
        this.workspaceId = workspaceId;
        this.sourceId = sourceId;
        this.pageId = pageId;
        this.runId = runId;
        this.status = "PENDING";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String pageRevisionId() { return pageRevisionId; }
    public Long workspaceId() { return workspaceId; }
    public String sourceId() { return sourceId; }
    public String pageId() { return pageId; }
    public String runId() { return runId; }
    public String status() { return status; }
    public String title() { return title; }
    public String publisher() { return publisher; }
    public String language() { return language; }
    public Instant publishedAt() { return publishedAt; }
    public Instant modifiedAt() { return modifiedAt; }
    public Instant retrievedAt() { return retrievedAt; }
    public String etag() { return etag; }
    public String lastModified() { return lastModified; }
    public String contentType() { return contentType; }
    public Long contentLength() { return contentLength; }
    public String contentHash() { return contentHash; }
    public String normalizedSnapshot() { return normalizedSnapshot; }
    public String contentPreview() { return contentPreview; }
    public String metadataJson() { return metadataJson; }
    public String errorCode() { return errorCode; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void fetched(
            String resolvedContentType,
            long resolvedContentLength,
            String resolvedEtag,
            String resolvedLastModified,
            Instant resolvedRetrievedAt,
            Instant now) {
        contentType = resolvedContentType;
        contentLength = resolvedContentLength;
        etag = resolvedEtag;
        lastModified = resolvedLastModified;
        retrievedAt = resolvedRetrievedAt;
        status = "FETCHED";
        updatedAt = now;
    }

    public void normalized(
            String resolvedTitle,
            String resolvedPublisher,
            String resolvedLanguage,
            Instant resolvedPublishedAt,
            Instant resolvedModifiedAt,
            String resolvedContentHash,
            String snapshot,
            String preview,
            String metadata,
            Instant now) {
        title = resolvedTitle;
        publisher = resolvedPublisher;
        language = resolvedLanguage;
        publishedAt = resolvedPublishedAt;
        modifiedAt = resolvedModifiedAt;
        contentHash = resolvedContentHash;
        normalizedSnapshot = snapshot;
        contentPreview = preview;
        metadataJson = metadata;
        status = "NORMALIZED";
        updatedAt = now;
    }

    public void complete(Instant now) {
        status = "COMPLETED";
        updatedAt = now;
    }

    public void unchanged(Instant now) {
        status = "UNCHANGED";
        updatedAt = now;
    }

    public void fail(String code, Instant now) {
        errorCode = code;
        status = "FAILED";
        updatedAt = now;
    }
}
