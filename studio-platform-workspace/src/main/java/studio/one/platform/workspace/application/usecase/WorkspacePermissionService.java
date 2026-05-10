package studio.one.platform.workspace.application.usecase;

import java.util.List;

import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.domain.model.WorkspaceRole;
import studio.one.platform.workspace.domain.model.WorkspacePermissionDefinition;

public interface WorkspacePermissionService {

    boolean isGranted(Long workspaceId, Long userId, String action);

    boolean isGranted(Long workspaceId, WorkspaceAccessContext actor, String action);

    void assertGranted(Long workspaceId, Long userId, String action);

    void assertGranted(Long workspaceId, WorkspaceAccessContext actor, String action);

    WorkspaceRole getEffectiveRole(Long workspaceId, Long userId);

    WorkspaceRole getEffectiveRole(Long workspaceId, WorkspaceAccessContext actor);

    List<String> getGrantedActions(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspacePermissionDefinition> getPermissionDefinitions();
}
