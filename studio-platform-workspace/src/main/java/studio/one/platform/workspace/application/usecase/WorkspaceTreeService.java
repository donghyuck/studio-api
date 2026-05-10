package studio.one.platform.workspace.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.workspace.application.command.ChangeWorkspaceParentCommand;
import studio.one.platform.workspace.application.command.CreateRootWorkspaceCommand;
import studio.one.platform.workspace.application.command.CreateWorkspaceCommand;
import studio.one.platform.workspace.application.command.UpdateWorkspaceCommand;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.command.WorkspaceListQuery;
import studio.one.platform.workspace.domain.model.WorkspaceRef;
import studio.one.platform.workspace.domain.model.WorkspaceTreeNode;

public interface WorkspaceTreeService {

    WorkspaceRef createRoot(CreateWorkspaceCommand command);

    WorkspaceRef createRoot(CreateRootWorkspaceCommand command);

    WorkspaceRef createChild(Long parentWorkspaceId, CreateWorkspaceCommand command);

    WorkspaceRef update(Long workspaceId, UpdateWorkspaceCommand command);

    WorkspaceRef changeParent(Long workspaceId, ChangeWorkspaceParentCommand command);

    WorkspaceRef getById(Long workspaceId, WorkspaceAccessContext actor);

    /**
     * @deprecated use {@link #getByPath(Long, String, WorkspaceAccessContext)} for company-scoped lookup.
     */
    @Deprecated(since = "2.x", forRemoval = false)
    WorkspaceRef getByPath(String path, WorkspaceAccessContext actor);

    WorkspaceRef getByPath(Long companyId, String path, WorkspaceAccessContext actor);

    Page<WorkspaceRef> list(WorkspaceListQuery query, Pageable pageable, WorkspaceAccessContext actor);

    List<WorkspaceRef> getChildren(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceRef> getAncestors(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceRef> getDescendants(Long workspaceId, WorkspaceAccessContext actor);

    WorkspaceTreeNode getTree(Long workspaceId, WorkspaceAccessContext actor);

    void archive(Long workspaceId, WorkspaceAccessContext actor);

    default WorkspaceRef archive(Long workspaceId, WorkspaceAccessContext actor, boolean cascade) {
        if (cascade) {
            throw new UnsupportedOperationException("Cascade archive is not supported by this WorkspaceTreeService");
        }
        archive(workspaceId, actor);
        return getById(workspaceId, actor);
    }

    default WorkspaceRef activate(Long workspaceId, WorkspaceAccessContext actor) {
        return activate(workspaceId, actor, false);
    }

    default WorkspaceRef activate(Long workspaceId, WorkspaceAccessContext actor, boolean cascade) {
        throw new UnsupportedOperationException("Workspace activation is not supported by this WorkspaceTreeService");
    }
}
