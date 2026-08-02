package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "web_knowledge_quota_usage")
public class WebKnowledgeQuotaUsageEntity {

    @Id
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "source_count", nullable = false)
    private long sourceCount;
    @Column(name = "active_page_count", nullable = false)
    private long activePageCount;
    @Column(name = "normalized_snapshot_bytes", nullable = false)
    private long normalizedSnapshotBytes;
    @Column(name = "reserved_page_count", nullable = false)
    private long reservedPageCount;
    @Column(name = "reserved_snapshot_bytes", nullable = false)
    private long reservedSnapshotBytes;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected WebKnowledgeQuotaUsageEntity() {
    }

    public WebKnowledgeQuotaUsageEntity(Long workspaceId, Instant now) {
        this.workspaceId = workspaceId;
        this.updatedAt = now;
    }

    public Long workspaceId() { return workspaceId; }
    public long reservedPageCount() { return reservedPageCount; }
    public long reservedSnapshotBytes() { return reservedSnapshotBytes; }

    public void observed(long sources, long pages, long snapshotBytes, Instant now) {
        sourceCount = Math.max(0L, sources);
        activePageCount = Math.max(0L, pages);
        normalizedSnapshotBytes = Math.max(0L, snapshotBytes);
        updatedAt = now;
    }

    public void reserve(long pages, long snapshotBytes, Instant now) {
        reservedPageCount += Math.max(0L, pages);
        reservedSnapshotBytes += Math.max(0L, snapshotBytes);
        updatedAt = now;
    }

    public void release(long pages, long snapshotBytes, Instant now) {
        reservedPageCount = Math.max(0L, reservedPageCount - Math.max(0L, pages));
        reservedSnapshotBytes = Math.max(0L, reservedSnapshotBytes - Math.max(0L, snapshotBytes));
        updatedAt = now;
    }
}
