package studio.one.platform.workspace.infrastructure.persistence.jpa;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.workspace.domain.model.WorkspaceMemberRef;
import studio.one.platform.workspace.domain.model.WorkspaceRole;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_PLATFORM_WORKSPACE_MEMBER")
@Getter
@Setter
@NoArgsConstructor
public class WorkspaceMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "WORKSPACE_ID", nullable = false)
    private Long workspaceId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    public WorkspaceMemberRef toRef(boolean inherited) {
        return new WorkspaceMemberRef(workspaceId, userId, role, inherited);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
