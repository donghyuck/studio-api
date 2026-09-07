package studio.one.platform.workspace.domain.model;

public record WorkspaceRef(
        Long id,
        Long companyId,
        Long teamId,
        Long parentId,
        Long rootId,
        String name,
        String slug,
        String path,
        int depth,
        WorkspaceVisibility visibility,
        WorkspaceAccessMode accessMode,
        boolean archived) {

    public WorkspaceRef(
            Long id,
            Long companyId,
            Long parentId,
            Long rootId,
            String name,
            String slug,
            String path,
            int depth,
            WorkspaceVisibility visibility,
            boolean archived) {
        this(id, companyId, null, parentId, rootId, name, slug, path, depth, visibility,
                WorkspaceAccessMode.INHERIT, archived);
    }

    public WorkspaceRef(
            Long id,
            Long parentId,
            Long rootId,
            String name,
            String slug,
            String path,
            int depth,
            WorkspaceVisibility visibility,
            boolean archived) {
        this(id, null, null, parentId, rootId, name, slug, path, depth, visibility,
                WorkspaceAccessMode.INHERIT, archived);
    }
}
