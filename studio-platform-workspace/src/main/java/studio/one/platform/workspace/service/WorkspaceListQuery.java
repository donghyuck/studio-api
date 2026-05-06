package studio.one.platform.workspace.service;

public record WorkspaceListQuery(
        String q,
        Long companyId,
        Long parentId,
        Boolean rootOnly,
        Boolean archived) {

    public WorkspaceListQuery(String q, Long parentId, Boolean rootOnly, Boolean archived) {
        this(q, null, parentId, rootOnly, archived);
    }

    public boolean rootOnlyEnabled() {
        return Boolean.TRUE.equals(rootOnly);
    }
}
