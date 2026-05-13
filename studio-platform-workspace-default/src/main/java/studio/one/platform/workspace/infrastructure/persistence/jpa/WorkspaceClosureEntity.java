package studio.one.platform.workspace.infrastructure.persistence.jpa;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

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
