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
@Table(name = "web_knowledge_revision")
public class WebKnowledgeRevisionEntity {

    @Id
    @Column(name = "revision_id", length = 80, nullable = false)
    private String revisionId;
    @Column(name = "source_id", length = 80, nullable = false)
    private String sourceId;
    @Column(name = "rag_job_id", length = 80)
    private String ragJobId;
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

    protected WebKnowledgeRevisionEntity() {
    }

    public WebKnowledgeRevisionEntity(String revisionId, String sourceId, Instant now) {
        this.revisionId = revisionId;
        this.sourceId = sourceId;
        this.status = "PENDING";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String revisionId() { return revisionId; }
    public String sourceId() { return sourceId; }
    public String ragJobId() { return ragJobId; }
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

    public void job(String jobId, Instant now) {
        this.ragJobId = jobId;
        this.updatedAt = now;
    }

    public void status(String value, Instant now) {
        this.status = value;
        this.updatedAt = now;
    }

    public void fetched(String contentType, long contentLength, String etag, String lastModified,
            Instant retrievedAt, Instant now) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.etag = etag;
        this.lastModified = lastModified;
        this.retrievedAt = retrievedAt;
        this.updatedAt = now;
    }

    public void normalized(String title, String publisher, String language, Instant publishedAt,
            Instant modifiedAt, String contentHash, String normalizedSnapshot, String preview,
            String metadataJson, Instant now) {
        this.title = title;
        this.publisher = publisher;
        this.language = language;
        this.publishedAt = publishedAt;
        this.modifiedAt = modifiedAt;
        this.contentHash = contentHash;
        this.normalizedSnapshot = normalizedSnapshot;
        this.contentPreview = preview;
        this.metadataJson = metadataJson;
        this.updatedAt = now;
    }

    public void fail(String code, Instant now) {
        this.status = "FAILED";
        this.errorCode = code;
        this.updatedAt = now;
    }
}
