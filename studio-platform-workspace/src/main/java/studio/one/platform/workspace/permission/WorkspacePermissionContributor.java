package studio.one.platform.workspace.permission;

import java.util.Collection;

public interface WorkspacePermissionContributor {

    Collection<WorkspacePermissionDefinition> permissions();

    Collection<WorkspaceRolePermissionMapping> defaultMappings();
}
