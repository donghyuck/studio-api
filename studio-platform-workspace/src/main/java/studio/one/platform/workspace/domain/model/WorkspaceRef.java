package studio.one.platform.workspace.domain.model;

public class WorkspaceRef {
    private final Long id;
    private final Long companyId;
    private final Long parentId;
    private final Long rootId;
    private final String name;
    private final String slug;
    private final String path;
    private final int depth;
    private final WorkspaceVisibility visibility;
    private final boolean archived;

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
        this.id = id;
        this.companyId = companyId;
        this.parentId = parentId;
        this.rootId = rootId;
        this.name = name;
        this.slug = slug;
        this.path = path;
        this.depth = depth;
        this.visibility = visibility;
        this.archived = archived;
    }

    public Long id() { return id; }

    public Long companyId() { return companyId; }

    public Long parentId() { return parentId; }

    public Long rootId() { return rootId; }

    public String name() { return name; }

    public String slug() { return slug; }

    public String path() { return path; }

    public int depth() { return depth; }

    public WorkspaceVisibility visibility() { return visibility; }

    public boolean archived() { return archived; }

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
