package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public record WorkspaceMemberListQuery(
        String keyword,
        WorkspaceRole role,
        Boolean inherited) {

    public static WorkspaceMemberListQuery all() {
        return new WorkspaceMemberListQuery(null, null, null);
    }

    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public boolean hasKeyword() {
        return normalizedKeyword() != null;
    }
}
