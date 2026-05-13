package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.application.error.WorkspaceValidationException;

public class WorkspaceAccessContext {

    private final Long userId;
    private final String username;
    private final boolean platformAdmin;

    public WorkspaceAccessContext(
            Long userId,
            String username,
            boolean platformAdmin) {
        this.userId = userId;
        this.username = username;
        this.platformAdmin = platformAdmin;
    }

    public Long userId() { return userId; }

    public String username() { return username; }

    public boolean platformAdmin() { return platformAdmin; }

public Long requireUserId() {
        if (userId == null || userId <= 0) {
            throw new WorkspaceValidationException("workspace actor userId is required");
        }
        return userId;
    }

    public String displayName() {
        return username != null && !username.isBlank() ? username : String.valueOf(userId);
    }

}
