package studio.one.platform.workspace.application.error;

import studio.one.platform.exception.PlatformRuntimeException;

public class WorkspaceValidationException extends PlatformRuntimeException {

    public WorkspaceValidationException(String message, Object... args) {
        super(WorkspaceErrors.BAD_REQUEST, message, args);
    }
}
