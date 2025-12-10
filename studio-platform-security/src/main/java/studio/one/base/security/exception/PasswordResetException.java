package studio.one.base.security.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.PlatformRuntimeException;

public class PasswordResetException extends PlatformRuntimeException {

    private static final long serialVersionUID = 1L;

    private static final ErrorType TYPE = ErrorType.of("error.security.password.reset",
            HttpStatus.INTERNAL_SERVER_ERROR);

    public PasswordResetException(String message, Object... args) {
        super(TYPE, message, args);
    }

    public PasswordResetException(String message, Throwable cause, Object... args) {
        super(TYPE, null, message, args);
        initCause(cause);
    }

    public static PasswordResetException of(Object... args) {
        return new PasswordResetException(TYPE.getId(), args);
    }
}
