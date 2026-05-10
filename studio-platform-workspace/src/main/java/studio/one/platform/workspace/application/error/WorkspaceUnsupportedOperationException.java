package studio.one.platform.workspace.application.error;

import studio.one.platform.exception.PlatformRuntimeException;

public class WorkspaceUnsupportedOperationException extends PlatformRuntimeException {

    public WorkspaceUnsupportedOperationException(String message, Object... args) {
        super(WorkspaceErrors.NOT_IMPLEMENTED, message, args);
    }
}
