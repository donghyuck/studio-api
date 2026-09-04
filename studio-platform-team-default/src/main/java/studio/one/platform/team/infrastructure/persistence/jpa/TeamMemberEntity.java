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
import studio.one.platform.team.domain.model.TeamMemberRef;
import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamRole;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_PLATFORM_TEAM_MEMBER")
@Getter
@Setter
@NoArgsConstructor
public class TeamMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "TEAM_ID", nullable = false)
    private Long teamId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    private TeamRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private TeamMemberStatus status = TeamMemberStatus.ACTIVE;

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

    public TeamMemberRef toRef() {
        return new TeamMemberRef(teamId, userId, role, status, createdAt, updatedAt);
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
            status = TeamMemberStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
