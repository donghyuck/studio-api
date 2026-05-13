package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public class WorkspaceMemberListQuery {

    private final String keyword;
    private final WorkspaceRole role;
    private final Boolean inherited;

    public WorkspaceMemberListQuery(
            String keyword,
            WorkspaceRole role,
            Boolean inherited) {
        this.keyword = keyword;
        this.role = role;
        this.inherited = inherited;
    }

    public String keyword() { return keyword; }

    public WorkspaceRole role() { return role; }

    public Boolean inherited() { return inherited; }

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
