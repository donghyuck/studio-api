package studio.one.platform.team.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.team.domain.model.TeamMigrationRef;
import studio.one.platform.team.domain.model.TeamMigrationStatus;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_PLATFORM_TEAM_MIGRATION_RUN")
@Getter
@Setter
@NoArgsConstructor
public class TeamMigrationRunEntity {
    @Id
    @Column(name = "RUN_ID", nullable = false, length = 36)
    private String runId;

    @Column(name = "IDEMPOTENCY_KEY", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "TARGET_TEAM_ID", nullable = false)
    private Long targetTeamId;

    @Column(name = "SOURCE_ROOT_IDS", nullable = false, length = 4000)
    private String sourceRootIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 40)
    private TeamMigrationStatus status = TeamMigrationStatus.DRAFT;

    @Column(name = "BEFORE_SNAPSHOT_REF", columnDefinition = "TEXT")
    private String beforeSnapshotReference;

    @Column(name = "WORKSPACE_SNAPSHOT_REF", columnDefinition = "TEXT")
    private String workspaceSnapshotReference;

    @Column(name = "KNOWLEDGE_SNAPSHOT_REF", columnDefinition = "TEXT")
    private String knowledgeSnapshotReference;

    @Column(name = "WORKSPACE_COUNT", nullable = false)
    private long workspaceCount;

    @Column(name = "MEMBER_COUNT", nullable = false)
    private long memberCount;

    @Column(name = "ATTACHMENT_COUNT", nullable = false)
    private long attachmentCount;

    @Column(name = "WIKI_COUNT", nullable = false)
    private long wikiCount;

    @Column(name = "WEB_SOURCE_COUNT", nullable = false)
    private long webSourceCount;

    @Column(name = "VECTOR_COUNT", nullable = false)
    private long vectorCount;

    @Column(name = "SOURCE_CHECKSUM", length = 128)
    private String sourceChecksum;

    @Column(name = "FAILURE_MESSAGE", length = 2000)
    private String failureMessage;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "UPDATED_BY", nullable = false)
    private Long updatedBy;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "LOCK_VERSION", nullable = false)
    private long lockVersion;

    public List<Long> sourceRootWorkspaceIds() {
        if (sourceRootIds == null || sourceRootIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(sourceRootIds.split(","))
                .map(Long::valueOf)
                .toList();
    }

    public TeamMigrationRef toRef() {
        return new TeamMigrationRef(
                runId,
                idempotencyKey,
                targetTeamId,
                sourceRootWorkspaceIds(),
                status,
                beforeSnapshotReference,
                workspaceSnapshotReference,
                knowledgeSnapshotReference,
                workspaceCount,
                memberCount,
                attachmentCount,
                wikiCount,
                webSourceCount,
                vectorCount,
                sourceChecksum,
                failureMessage,
                createdAt,
                updatedAt);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (status == null) {
            status = TeamMigrationStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
