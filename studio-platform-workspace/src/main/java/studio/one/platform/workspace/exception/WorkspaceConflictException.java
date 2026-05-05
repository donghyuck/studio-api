package studio.one.platform.workspace.exception;

import studio.one.platform.exception.PlatformRuntimeException;

public class WorkspaceConflictException extends PlatformRuntimeException {

    public WorkspaceConflictException(String message, Object... args) {
        super(WorkspaceErrors.CONFLICT, message, args);
    }
}
