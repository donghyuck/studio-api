package studio.one.platform.workspace.application.command;

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

}
