package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.application.error.WorkspaceValidationException;

public record WorkspaceAccessContext(
        Long userId,
        String username,
        boolean platformAdmin) {

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
