package studio.one.application.wiki.domain.model;

import java.util.List;

import studio.one.platform.workspace.domain.model.WorkspaceRole;
import studio.one.platform.workspace.domain.model.WorkspacePermissionDefinition;
import studio.one.platform.workspace.domain.model.WorkspaceRolePermissionMapping;

public final class WikiPermissionActions {

    public static final String PAGE_READ = "wiki.page.read";
    public static final String PAGE_CREATE = "wiki.page.create";
    public static final String PAGE_UPDATE = "wiki.page.update";
    public static final String PAGE_DELETE = "wiki.page.delete";
    public static final String PAGE_REVERT = "wiki.page.revert";
    public static final String HISTORY_READ = "wiki.page.history.read";
    public static final String ADMIN = "wiki.admin";

    private WikiPermissionActions() {
    }

    public static List<WorkspacePermissionDefinition> definitions() {
        return List.of(
                new WorkspacePermissionDefinition(PAGE_READ, "Read workspace wiki pages"),
                new WorkspacePermissionDefinition(PAGE_CREATE, "Create workspace wiki pages"),
                new WorkspacePermissionDefinition(PAGE_UPDATE, "Update workspace wiki pages"),
                new WorkspacePermissionDefinition(PAGE_DELETE, "Archive workspace wiki pages"),
                new WorkspacePermissionDefinition(PAGE_REVERT, "Revert workspace wiki page revisions"),
                new WorkspacePermissionDefinition(HISTORY_READ, "Read workspace wiki page revisions"),
                new WorkspacePermissionDefinition(ADMIN, "Manage workspace wiki administration pages"));
    }

    public static List<WorkspaceRolePermissionMapping> defaultMappings() {
        return List.of(
                new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, PAGE_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, HISTORY_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.EDITOR, PAGE_CREATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.EDITOR, PAGE_UPDATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.EDITOR, PAGE_REVERT),
                new WorkspaceRolePermissionMapping(WorkspaceRole.ADMIN, PAGE_DELETE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.ADMIN, ADMIN),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, PAGE_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, PAGE_CREATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, PAGE_UPDATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, PAGE_DELETE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, PAGE_REVERT),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, HISTORY_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, ADMIN));
    }
}
