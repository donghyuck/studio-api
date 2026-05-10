package studio.one.platform.workspace.domain.model;

import java.util.List;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public final class WorkspacePermissionActions {

    public static final String READ = "workspace.read";
    public static final String CREATE = "workspace.create";
    public static final String UPDATE = "workspace.update";
    public static final String ARCHIVE = "workspace.archive";
    public static final String ACTIVATE = "workspace.activate";
    public static final String TREE_READ = "workspace.tree.read";
    public static final String MEMBER_READ = "workspace.member.read";
    public static final String MEMBER_MANAGE = "workspace.member.manage";
    public static final String PERMISSION_READ = "workspace.permission.read";
    public static final String PERMISSION_MANAGE = "workspace.permission.manage";

    private WorkspacePermissionActions() {
    }

    public static List<WorkspacePermissionDefinition> definitions() {
        return List.of(
                new WorkspacePermissionDefinition(READ, "Read workspace metadata"),
                new WorkspacePermissionDefinition(CREATE, "Create child workspace"),
                new WorkspacePermissionDefinition(UPDATE, "Update workspace metadata"),
                new WorkspacePermissionDefinition(ARCHIVE, "Archive workspace"),
                new WorkspacePermissionDefinition(ACTIVATE, "Activate archived workspace"),
                new WorkspacePermissionDefinition(TREE_READ, "Read workspace tree"),
                new WorkspacePermissionDefinition(MEMBER_READ, "Read workspace members"),
                new WorkspacePermissionDefinition(MEMBER_MANAGE, "Manage workspace members"),
                new WorkspacePermissionDefinition(PERMISSION_READ, "Read workspace permissions"),
                new WorkspacePermissionDefinition(PERMISSION_MANAGE, "Manage workspace permissions"));
    }

    public static List<WorkspaceRolePermissionMapping> defaultMappings() {
        return List.of(
                new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, TREE_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, MEMBER_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, PERMISSION_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.EDITOR, CREATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.EDITOR, UPDATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.ADMIN, ARCHIVE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.ADMIN, ACTIVATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.ADMIN, MEMBER_MANAGE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.ADMIN, PERMISSION_MANAGE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, CREATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, UPDATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, ARCHIVE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, ACTIVATE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, TREE_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, MEMBER_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, MEMBER_MANAGE),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, PERMISSION_READ),
                new WorkspaceRolePermissionMapping(WorkspaceRole.OWNER, PERMISSION_MANAGE));
    }
}
