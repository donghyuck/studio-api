package studio.one.platform.workspace.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_PLATFORM_WORKSPACE_CLOSURE")
@Getter
@Setter
@NoArgsConstructor
public class WorkspaceClosureEntity {

    @EmbeddedId
    private WorkspaceClosureId id;

    @Column(name = "DEPTH", nullable = false)
    private int depth;

    public WorkspaceClosureEntity(Long ancestorId, Long descendantId, int depth) {
        this.id = new WorkspaceClosureId(ancestorId, descendantId);
        this.depth = depth;
    }

    public Long ancestorId() {
        return id.getAncestorId();
    }

    public Long descendantId() {
        return id.getDescendantId();
    }
}
