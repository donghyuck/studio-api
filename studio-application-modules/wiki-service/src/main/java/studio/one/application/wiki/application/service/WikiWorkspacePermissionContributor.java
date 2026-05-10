package studio.one.application.wiki.application.service;

import java.util.Collection;

import studio.one.application.wiki.domain.model.WikiPermissionActions;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionContributor;
import studio.one.platform.workspace.domain.model.WorkspacePermissionDefinition;
import studio.one.platform.workspace.domain.model.WorkspaceRolePermissionMapping;

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
