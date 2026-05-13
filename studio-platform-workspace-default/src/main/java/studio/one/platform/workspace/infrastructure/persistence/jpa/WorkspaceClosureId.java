package studio.one.platform.workspace.infrastructure.persistence.jpa;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WorkspaceClosureId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "ANCESTOR_ID", nullable = false)
    private Long ancestorId;

    @Column(name = "DESCENDANT_ID", nullable = false)
    private Long descendantId;
}
