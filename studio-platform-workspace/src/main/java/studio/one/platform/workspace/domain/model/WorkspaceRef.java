package studio.one.platform.workspace.domain.model;

public record WorkspaceRef(
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
        this(id, null, parentId, rootId, name, slug, path, depth, visibility, archived);
    }
}
