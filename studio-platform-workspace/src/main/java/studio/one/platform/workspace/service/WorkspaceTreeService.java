package studio.one.platform.workspace.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.workspace.model.WorkspaceRef;
import studio.one.platform.workspace.model.WorkspaceTreeNode;

public interface WorkspaceTreeService {

    WorkspaceRef createRoot(CreateWorkspaceCommand command);

    default WorkspaceRef createRoot(CreateRootWorkspaceCommand command) {
        return createRoot(new CreateWorkspaceCommand(
                command.name(),
                command.slug(),
                command.visibility(),
                command.actor()));
    }

    WorkspaceRef createChild(Long parentWorkspaceId, CreateWorkspaceCommand command);

    WorkspaceRef update(Long workspaceId, UpdateWorkspaceCommand command);

    WorkspaceRef changeParent(Long workspaceId, ChangeWorkspaceParentCommand command);

    WorkspaceRef getById(Long workspaceId, WorkspaceAccessContext actor);

    /**
     * @deprecated use {@link #getByPath(Long, String, WorkspaceAccessContext)} for company-scoped lookup.
     */
    @Deprecated(since = "2.x", forRemoval = false)
    WorkspaceRef getByPath(String path, WorkspaceAccessContext actor);

    default WorkspaceRef getByPath(Long companyId, String path, WorkspaceAccessContext actor) {
        return getByPath(path, actor);
    }

    Page<WorkspaceRef> list(WorkspaceListQuery query, Pageable pageable, WorkspaceAccessContext actor);

    List<WorkspaceRef> getChildren(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceRef> getAncestors(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceRef> getDescendants(Long workspaceId, WorkspaceAccessContext actor);

    WorkspaceTreeNode getTree(Long workspaceId, WorkspaceAccessContext actor);

    default void archive(Long workspaceId, WorkspaceAccessContext actor) {
        archive(workspaceId, actor, false);
    }

    WorkspaceRef archive(Long workspaceId, WorkspaceAccessContext actor, boolean cascade);

    default WorkspaceRef activate(Long workspaceId, WorkspaceAccessContext actor) {
        return activate(workspaceId, actor, false);
    }

    WorkspaceRef activate(Long workspaceId, WorkspaceAccessContext actor, boolean cascade);
}
