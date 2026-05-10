package studio.one.platform.workspace.application.error;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;

public final class WorkspaceErrors {

    public static final ErrorType NOT_FOUND = ErrorType.of("error.workspace.not-found", HttpStatus.NOT_FOUND);
    public static final ErrorType CONFLICT = ErrorType.of("error.workspace.conflict", HttpStatus.CONFLICT);
    public static final ErrorType BAD_REQUEST = ErrorType.of("error.workspace.bad-request", HttpStatus.BAD_REQUEST);
    public static final ErrorType NOT_IMPLEMENTED = ErrorType.of("error.workspace.not-implemented", HttpStatus.NOT_IMPLEMENTED);

    private WorkspaceErrors() {
    }
}
