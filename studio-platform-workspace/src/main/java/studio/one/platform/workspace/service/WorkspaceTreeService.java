package studio.one.platform.workspace.service;

import java.util.List;

import studio.one.platform.workspace.model.WorkspaceRef;
import studio.one.platform.workspace.model.WorkspaceTreeNode;

public interface WorkspaceTreeService {

    WorkspaceRef createRoot(CreateWorkspaceCommand command);

    WorkspaceRef createChild(Long parentWorkspaceId, CreateWorkspaceCommand command);

    WorkspaceRef update(Long workspaceId, UpdateWorkspaceCommand command);

    WorkspaceRef getById(Long workspaceId, WorkspaceAccessContext actor);

    WorkspaceRef getByPath(String path, WorkspaceAccessContext actor);

    List<WorkspaceRef> getChildren(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceRef> getAncestors(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceRef> getDescendants(Long workspaceId, WorkspaceAccessContext actor);

    WorkspaceTreeNode getTree(Long workspaceId, WorkspaceAccessContext actor);

    void archive(Long workspaceId, WorkspaceAccessContext actor);
}
