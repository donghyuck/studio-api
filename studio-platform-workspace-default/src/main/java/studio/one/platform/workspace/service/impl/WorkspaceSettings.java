package studio.one.platform.workspace.service.impl;

public record WorkspaceSettings(
        int maxDepth,
        int maxChildrenPerNode,
        int slugMaxLength,
        boolean inheritParentRole,
        boolean companyRequired) {

    public static WorkspaceSettings defaults() {
        return new WorkspaceSettings(10, 200, 100, true, false);
    }
}
