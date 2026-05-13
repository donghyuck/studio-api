package studio.one.platform.workspace.domain.model;

import java.util.List;

public class WorkspaceTreeNode {

    private final WorkspaceRef workspace;
    private final List<WorkspaceTreeNode> children;

    public WorkspaceTreeNode(WorkspaceRef workspace, List<WorkspaceTreeNode> children) {
        this.workspace = workspace;
        this.children = children == null ? List.of() : List.copyOf(children);
    }

    public WorkspaceRef workspace() { return workspace; }

    public List<WorkspaceTreeNode> children() { return children; }
}
