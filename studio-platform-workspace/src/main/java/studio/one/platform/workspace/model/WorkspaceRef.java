package studio.one.platform.workspace.model;

public record WorkspaceRef(
        Long id,
        Long parentId,
        Long rootId,
        String name,
        String slug,
        String path,
        int depth,
        WorkspaceVisibility visibility,
        boolean archived) {
}
