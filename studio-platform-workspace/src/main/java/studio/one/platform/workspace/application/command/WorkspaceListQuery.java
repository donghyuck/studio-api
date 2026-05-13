package studio.one.platform.workspace.application.command;

import java.util.Objects;

public class WorkspaceListQuery {

    private final String q;
    private final Long companyId;
    private final Long parentId;
    private final Boolean rootOnly;
    private final Boolean archived;

    public WorkspaceListQuery(
            String q,
            Long companyId,
            Long parentId,
            Boolean rootOnly,
            Boolean archived) {
        this.q = q;
        this.companyId = companyId;
        this.parentId = parentId;
        this.rootOnly = rootOnly;
        this.archived = archived;
    }

    public String q() { return q; }

    public Long companyId() { return companyId; }

    public Long parentId() { return parentId; }

    public Boolean rootOnly() { return rootOnly; }

    public Boolean archived() { return archived; }

public WorkspaceListQuery(String q, Long parentId, Boolean rootOnly, Boolean archived) {
        this(q, null, parentId, rootOnly, archived);
    }

    public boolean rootOnlyEnabled() {
        return Boolean.TRUE.equals(rootOnly);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkspaceListQuery)) {
            return false;
        }
        WorkspaceListQuery that = (WorkspaceListQuery) o;
        return Objects.equals(q, that.q)
                && Objects.equals(companyId, that.companyId)
                && Objects.equals(parentId, that.parentId)
                && Objects.equals(rootOnly, that.rootOnly)
                && Objects.equals(archived, that.archived);
    }

    @Override
    public int hashCode() {
        return Objects.hash(q, companyId, parentId, rootOnly, archived);
    }
}
