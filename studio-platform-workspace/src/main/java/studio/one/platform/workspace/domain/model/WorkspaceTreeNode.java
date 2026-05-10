package studio.one.platform.workspace.domain.model;

import java.util.List;

public record WorkspaceTreeNode(
        WorkspaceRef workspace,
        List<WorkspaceTreeNode> children) {

    public WorkspaceTreeNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
