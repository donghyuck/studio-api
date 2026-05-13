package studio.one.platform.workspace.domain.model;

public class WorkspacePermissionDefinition {
    private final String action;
    private final String description;

    public WorkspacePermissionDefinition(
            String action,
            String description) {
        this.action = action;
        this.description = description;
    }

    public String action() {
        return action;
    }

    public String description() {
        return description;
    }

}
