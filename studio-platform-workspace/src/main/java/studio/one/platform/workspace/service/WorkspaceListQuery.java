package studio.one.platform.workspace.service;

public record WorkspaceListQuery(
        String q,
        Long parentId,
        Boolean rootOnly,
        Boolean archived) {

    public boolean rootOnlyEnabled() {
        return Boolean.TRUE.equals(rootOnly);
    }
}
