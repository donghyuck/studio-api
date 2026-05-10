package studio.one.platform.workspace.application.usecase;

import java.util.Collection;

import studio.one.platform.workspace.domain.model.WorkspacePermissionDefinition;
import studio.one.platform.workspace.domain.model.WorkspaceRolePermissionMapping;

public interface WorkspacePermissionContributor {

    Collection<WorkspacePermissionDefinition> permissions();

    Collection<WorkspaceRolePermissionMapping> defaultMappings();
}
