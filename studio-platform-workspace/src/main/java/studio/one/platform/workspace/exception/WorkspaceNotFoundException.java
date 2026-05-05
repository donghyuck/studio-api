package studio.one.platform.workspace.exception;

import studio.one.platform.exception.PlatformRuntimeException;

public class WorkspaceNotFoundException extends PlatformRuntimeException {

    public WorkspaceNotFoundException(String message, Object... args) {
        super(WorkspaceErrors.NOT_FOUND, message, args);
    }
}
