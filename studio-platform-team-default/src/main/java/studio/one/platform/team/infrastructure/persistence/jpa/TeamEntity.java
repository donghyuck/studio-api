package studio.one.platform.team.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamRagReplyMode;
import studio.one.platform.team.domain.model.TeamRef;
import studio.one.platform.team.domain.model.TeamStatus;
import studio.one.platform.team.domain.model.TeamVisibility;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_PLATFORM_TEAM")
@Getter
@Setter
@NoArgsConstructor
public class TeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TEAM_ID", nullable = false)
    private Long teamId;

    @Column(name = "COMPANY_ID")
    private Long companyId;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "SLUG", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "VISIBILITY", nullable = false, length = 20)
    private TeamVisibility visibility = TeamVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "JOIN_POLICY", nullable = false, length = 20)
    private TeamJoinPolicy joinPolicy = TeamJoinPolicy.INVITE_ONLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private TeamStatus status = TeamStatus.ACTIVE;

    @Column(name = "RAG_ENABLED", nullable = false)
    private boolean ragEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "RAG_REPLY_MODE", nullable = false, length = 20)
    private TeamRagReplyMode ragReplyMode = TeamRagReplyMode.MENTION;

    @Column(name = "PERMISSION_VERSION", nullable = false)
    private long permissionVersion = 1L;

    @Column(name = "ARCHIVED_AT")
    private Instant archivedAt;

    @Column(name = "ARCHIVED_BY")
    private Long archivedBy;

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

    public TeamRef toRef() {
        return new TeamRef(teamId, companyId, name, slug, description, visibility, joinPolicy,
                status, ragEnabled, ragReplyMode, permissionVersion, createdAt, updatedAt);
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
        if (visibility == null) {
            visibility = TeamVisibility.PRIVATE;
        }
        if (joinPolicy == null) {
            joinPolicy = TeamJoinPolicy.INVITE_ONLY;
        }
        if (status == null) {
            status = TeamStatus.ACTIVE;
        }
        if (ragReplyMode == null) {
            ragReplyMode = TeamRagReplyMode.MENTION;
        }
        if (permissionVersion <= 0) {
            permissionVersion = 1L;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
