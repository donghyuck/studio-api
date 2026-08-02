package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "web_knowledge_page")
public class WebKnowledgePageEntity {

    @Id
    @Column(name = "page_id", length = 80, nullable = false)
    private String pageId;
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "source_id", length = 80, nullable = false)
    private String sourceId;
    @Column(name = "normalized_url", length = 2048, nullable = false)
    private String normalizedUrl;
    @Column(name = "normalized_url_hash", length = 64, nullable = false)
    private String normalizedUrlHash;
    @Column(name = "canonical_url", length = 2048)
    private String canonicalUrl;
    @Column(name = "canonical_url_hash", length = 64)
    private String canonicalUrlHash;
    @Column(name = "current_page_revision_id", length = 80)
    private String currentPageRevisionId;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Column(name = "missing_run_count", nullable = false)
    private int missingRunCount;
    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected WebKnowledgePageEntity() {
    }

    public WebKnowledgePageEntity(
            String pageId,
            Long workspaceId,
            String sourceId,
            String normalizedUrl,
            String normalizedUrlHash,
            Instant now) {
        this.pageId = pageId;
        this.workspaceId = workspaceId;
        this.sourceId = sourceId;
        this.normalizedUrl = normalizedUrl;
        this.normalizedUrlHash = normalizedUrlHash;
        this.active = true;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String pageId() { return pageId; }
    public Long workspaceId() { return workspaceId; }
    public String sourceId() { return sourceId; }
    public String normalizedUrl() { return normalizedUrl; }
    public String normalizedUrlHash() { return normalizedUrlHash; }
    public String canonicalUrl() { return canonicalUrl; }
    public String currentPageRevisionId() { return currentPageRevisionId; }
    public boolean active() { return active; }
    public int missingRunCount() { return missingRunCount; }
    public Instant firstSeenAt() { return firstSeenAt; }
    public Instant lastSeenAt() { return lastSeenAt; }
    public Instant updatedAt() { return updatedAt; }

    public void seen(String canonical, String canonicalHash, Instant now) {
        canonicalUrl = canonical;
        canonicalUrlHash = canonicalHash;
        active = true;
        missingRunCount = 0;
        lastSeenAt = now;
        updatedAt = now;
    }

    public void currentRevision(String revisionId, Instant now) {
        currentPageRevisionId = revisionId;
        active = true;
        missingRunCount = 0;
        lastSeenAt = now;
        updatedAt = now;
    }

    public void missing(Instant now) {
        missingRunCount++;
        updatedAt = now;
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }
}
