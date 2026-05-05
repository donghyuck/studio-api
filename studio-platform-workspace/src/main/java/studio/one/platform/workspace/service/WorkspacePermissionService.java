package studio.one.platform.workspace.service;

import java.util.List;

import studio.one.platform.workspace.model.WorkspaceRole;
import studio.one.platform.workspace.permission.WorkspacePermissionDefinition;

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
