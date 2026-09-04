package studio.one.platform.workspace.application.command;

public record WorkspaceListQuery(
        String q,
        Long companyId,
        Long teamId,
        Long parentId,
        Boolean rootOnly,
        Boolean archived) {

    public WorkspaceListQuery(
            String q,
            Long companyId,
            Long parentId,
            Boolean rootOnly,
            Boolean archived) {
        this(q, companyId, null, parentId, rootOnly, archived);
    }

    public WorkspaceListQuery(String q, Long parentId, Boolean rootOnly, Boolean archived) {
        this(q, null, null, parentId, rootOnly, archived);
    }

    public boolean rootOnlyEnabled() {
        return Boolean.TRUE.equals(rootOnly);
    }
}
