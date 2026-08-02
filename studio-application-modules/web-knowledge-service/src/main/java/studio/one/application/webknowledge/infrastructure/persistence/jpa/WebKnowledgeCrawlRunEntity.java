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
@Table(name = "web_knowledge_crawl_run")
public class WebKnowledgeCrawlRunEntity {

    @Id
    @Column(name = "run_id", length = 80, nullable = false)
    private String runId;
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "source_id", length = 80, nullable = false)
    private String sourceId;
    @Column(name = "retry_of_run_id", length = 80)
    private String retryOfRunId;
    @Column(name = "status", length = 32, nullable = false)
    private String status;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "policy_json", length = Length.LONG32, nullable = false)
    private String policyJson;
    @Column(name = "policy_hash", length = 64, nullable = false)
    private String policyHash;
    @Column(name = "requested_by", length = 160)
    private String requestedBy;
    @Column(name = "discovered_count", nullable = false)
    private int discoveredCount;
    @Column(name = "fetched_count", nullable = false)
    private int fetchedCount;
    @Column(name = "indexed_count", nullable = false)
    private int indexedCount;
    @Column(name = "unchanged_count", nullable = false)
    private int unchangedCount;
    @Column(name = "updated_count", nullable = false)
    private int updatedCount;
    @Column(name = "removed_count", nullable = false)
    private int removedCount;
    @Column(name = "failed_count", nullable = false)
    private int failedCount;
    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;
    @Column(name = "response_bytes", nullable = false)
    private long responseBytes;
    @Column(name = "normalized_chars", nullable = false)
    private long normalizedChars;
    @Column(name = "reserved_page_count", nullable = false)
    private long reservedPageCount;
    @Column(name = "reserved_snapshot_bytes", nullable = false)
    private long reservedSnapshotBytes;
    @Column(name = "quota_released_at")
    private Instant quotaReleasedAt;
    @Column(name = "truncated", nullable = false)
    private boolean truncated;
    @Column(name = "truncation_reason", length = 80)
    private String truncationReason;
    @Column(name = "error_code", length = 80)
    private String errorCode;
    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;
    @Column(name = "lease_owner", length = 160)
    private String leaseOwner;
    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected WebKnowledgeCrawlRunEntity() {
    }

    public WebKnowledgeCrawlRunEntity(
            String runId,
            Long workspaceId,
            String sourceId,
            String retryOfRunId,
            String policyJson,
            String policyHash,
            String requestedBy,
            Instant now) {
        this.runId = runId;
        this.workspaceId = workspaceId;
        this.sourceId = sourceId;
        this.retryOfRunId = retryOfRunId;
        this.status = "PENDING";
        this.policyJson = policyJson;
        this.policyHash = policyHash;
        this.requestedBy = requestedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String runId() { return runId; }
    public Long workspaceId() { return workspaceId; }
    public String sourceId() { return sourceId; }
    public String retryOfRunId() { return retryOfRunId; }
    public String status() { return status; }
    public String policyJson() { return policyJson; }
    public String policyHash() { return policyHash; }
    public String requestedBy() { return requestedBy; }
    public int discoveredCount() { return discoveredCount; }
    public int fetchedCount() { return fetchedCount; }
    public int indexedCount() { return indexedCount; }
    public int unchangedCount() { return unchangedCount; }
    public int updatedCount() { return updatedCount; }
    public int removedCount() { return removedCount; }
    public int failedCount() { return failedCount; }
    public int skippedCount() { return skippedCount; }
    public long responseBytes() { return responseBytes; }
    public long normalizedChars() { return normalizedChars; }
    public long reservedPageCount() { return reservedPageCount; }
    public long reservedSnapshotBytes() { return reservedSnapshotBytes; }
    public Instant quotaReleasedAt() { return quotaReleasedAt; }
    public boolean truncated() { return truncated; }
    public String truncationReason() { return truncationReason; }
    public String errorCode() { return errorCode; }
    public Instant cancelRequestedAt() { return cancelRequestedAt; }
    public String leaseOwner() { return leaseOwner; }
    public Instant leaseExpiresAt() { return leaseExpiresAt; }
    public Instant heartbeatAt() { return heartbeatAt; }
    public int attemptNo() { return attemptNo; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void start(String owner, Instant leaseUntil, Instant now) {
        status = "DISCOVERING";
        leaseOwner = owner;
        leaseExpiresAt = leaseUntil;
        heartbeatAt = now;
        attemptNo++;
        if (startedAt == null) {
            startedAt = now;
        }
        updatedAt = now;
    }

    public void heartbeat(String owner, Instant leaseUntil, Instant now) {
        leaseOwner = owner;
        leaseExpiresAt = leaseUntil;
        heartbeatAt = now;
        updatedAt = now;
    }

    public void status(String value, Instant now) {
        status = value;
        updatedAt = now;
    }

    public void progress(
            int discovered,
            int fetched,
            int indexed,
            int unchanged,
            int updated,
            int removed,
            int failed,
            int skipped,
            long bytes,
            long chars,
            Instant now) {
        discoveredCount = discovered;
        fetchedCount = fetched;
        indexedCount = indexed;
        unchangedCount = unchanged;
        updatedCount = updated;
        removedCount = removed;
        failedCount = failed;
        skippedCount = skipped;
        responseBytes = bytes;
        normalizedChars = chars;
        updatedAt = now;
    }

    public void truncate(String reason, Instant now) {
        truncated = true;
        truncationReason = reason;
        updatedAt = now;
    }

    public void requestCancel(Instant now) {
        cancelRequestedAt = now;
        updatedAt = now;
    }

    public void quotaReservation(long pages, long snapshotBytes, Instant now) {
        reservedPageCount = Math.max(0L, pages);
        reservedSnapshotBytes = Math.max(0L, snapshotBytes);
        quotaReleasedAt = null;
        updatedAt = now;
    }

    public void quotaReleased(Instant now) {
        quotaReleasedAt = now;
        updatedAt = now;
    }

    public void complete(String finalStatus, Instant now) {
        status = finalStatus;
        completedAt = now;
        leaseOwner = null;
        leaseExpiresAt = null;
        updatedAt = now;
    }

    public void fail(String code, Instant now) {
        errorCode = code;
        complete("FAILED", now);
    }
}
