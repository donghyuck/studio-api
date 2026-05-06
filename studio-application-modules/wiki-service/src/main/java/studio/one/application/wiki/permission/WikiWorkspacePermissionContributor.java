package studio.one.application.wiki.permission;

import java.util.Collection;

import studio.one.platform.workspace.permission.WorkspacePermissionContributor;
import studio.one.platform.workspace.permission.WorkspacePermissionDefinition;
import studio.one.platform.workspace.permission.WorkspaceRolePermissionMapping;

public class WikiWorkspacePermissionContributor implements WorkspacePermissionContributor {

    @Override
    public Collection<WorkspacePermissionDefinition> permissions() {
        return WikiPermissionActions.definitions();
    }

    @Override
    public Collection<WorkspaceRolePermissionMapping> defaultMappings() {
        return WikiPermissionActions.defaultMappings();
    }
}
