package studio.one.platform.workspace.persistence.jpa;

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
import studio.one.platform.workspace.model.WorkspaceRef;
import studio.one.platform.workspace.model.WorkspaceVisibility;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_PLATFORM_WORKSPACE")
@Getter
@Setter
@NoArgsConstructor
public class WorkspaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "WORKSPACE_ID", nullable = false)
    private Long workspaceId;

    @Column(name = "PARENT_ID")
    private Long parentId;

    @Column(name = "ROOT_ID")
    private Long rootId;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "SLUG", nullable = false, length = 100)
    private String slug;

    @Column(name = "PATH", nullable = false, length = 1024)
    private String path;

    @Column(name = "DEPTH", nullable = false)
    private int depth;

    @Column(name = "POSITION", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "VISIBILITY", nullable = false, length = 20)
    private WorkspaceVisibility visibility = WorkspaceVisibility.PRIVATE;

    @Column(name = "ARCHIVED", nullable = false)
    private boolean archived;

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

    public WorkspaceRef toRef() {
        return new WorkspaceRef(
                workspaceId,
                parentId,
                rootId,
                name,
                slug,
                path,
                depth,
                visibility,
                archived);
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
            visibility = WorkspaceVisibility.PRIVATE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
