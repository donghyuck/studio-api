package studio.one.platform.workspace.application.command;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkspaceMemberListQuery)) {
            return false;
        }
        WorkspaceMemberListQuery that = (WorkspaceMemberListQuery) o;
        return Objects.equals(keyword, that.keyword)
                && role == that.role
                && Objects.equals(inherited, that.inherited);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, role, inherited);
    }
}
