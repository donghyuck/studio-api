package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "web_knowledge_crawl_item")
public class WebKnowledgeCrawlItemEntity {

    @Id
    @Column(name = "item_id", length = 80, nullable = false)
    private String itemId;
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "run_id", length = 80, nullable = false)
    private String runId;
    @Column(name = "source_id", length = 80, nullable = false)
    private String sourceId;
    @Column(name = "normalized_url", length = 2048, nullable = false)
    private String normalizedUrl;
    @Column(name = "normalized_url_hash", length = 64, nullable = false)
    private String normalizedUrlHash;
    @Column(name = "parent_url_hash", length = 64)
    private String parentUrlHash;
    @Column(name = "depth", nullable = false)
    private int depth;
    @Column(name = "discovery_order", nullable = false)
    private int discoveryOrder;
    @Column(name = "status", length = 32, nullable = false)
    private String status;
    @Column(name = "page_id", length = 80)
    private String pageId;
    @Column(name = "page_revision_id", length = 80)
    private String pageRevisionId;
    @Column(name = "error_code", length = 80)
    private String errorCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected WebKnowledgeCrawlItemEntity() {
    }

    public WebKnowledgeCrawlItemEntity(
            String itemId,
            Long workspaceId,
            String runId,
            String sourceId,
            String normalizedUrl,
            String normalizedUrlHash,
            String parentUrlHash,
            int depth,
            int discoveryOrder,
            Instant now) {
        this.itemId = itemId;
        this.workspaceId = workspaceId;
        this.runId = runId;
        this.sourceId = sourceId;
        this.normalizedUrl = normalizedUrl;
        this.normalizedUrlHash = normalizedUrlHash;
        this.parentUrlHash = parentUrlHash;
        this.depth = depth;
        this.discoveryOrder = discoveryOrder;
        this.status = "DISCOVERED";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String itemId() { return itemId; }
    public Long workspaceId() { return workspaceId; }
    public String runId() { return runId; }
    public String sourceId() { return sourceId; }
    public String normalizedUrl() { return normalizedUrl; }
    public String normalizedUrlHash() { return normalizedUrlHash; }
    public String parentUrlHash() { return parentUrlHash; }
    public int depth() { return depth; }
    public int discoveryOrder() { return discoveryOrder; }
    public String status() { return status; }
    public String pageId() { return pageId; }
    public String pageRevisionId() { return pageRevisionId; }
    public String errorCode() { return errorCode; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void status(String value, Instant now) {
        status = value;
        updatedAt = now;
    }

    public void indexed(String resolvedPageId, String resolvedPageRevisionId, Instant now) {
        pageId = resolvedPageId;
        pageRevisionId = resolvedPageRevisionId;
        status = "INDEXED";
        updatedAt = now;
    }

    public void fail(String code, Instant now) {
        errorCode = code;
        status = "FAILED";
        updatedAt = now;
    }
}
