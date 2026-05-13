package studio.one.platform.workspace.application.service;

public class WorkspaceSettings {
    private final int maxDepth;
    private final int maxChildrenPerNode;
    private final int slugMaxLength;
    private final boolean inheritParentRole;
    private final boolean companyRequired;
    private final boolean companyScopeEnforced;

    public WorkspaceSettings(
            int maxDepth,
            int maxChildrenPerNode,
            int slugMaxLength,
            boolean inheritParentRole,
            boolean companyRequired,
            boolean companyScopeEnforced) {
        this.maxDepth = maxDepth;
        this.maxChildrenPerNode = maxChildrenPerNode;
        this.slugMaxLength = slugMaxLength;
        this.inheritParentRole = inheritParentRole;
        this.companyRequired = companyRequired;
        this.companyScopeEnforced = companyScopeEnforced;
    }

    public int maxDepth() { return maxDepth; }

    public int maxChildrenPerNode() { return maxChildrenPerNode; }

    public int slugMaxLength() { return slugMaxLength; }

    public boolean inheritParentRole() { return inheritParentRole; }

    public boolean companyRequired() { return companyRequired; }

    public boolean companyScopeEnforced() { return companyScopeEnforced; }

    public static WorkspaceSettings defaults() {
        return new WorkspaceSettings(10, 200, 100, true, false, false);
    }

}
