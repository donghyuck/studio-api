package studio.one.platform.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;

public class UnAuthorizedException extends PlatformRuntimeException {

    private static final ErrorType TYPE = ErrorType.of("error.security.unauthorized", HttpStatus.UNAUTHORIZED );

    public UnAuthorizedException(String message, Object... args) {
        super(TYPE, message, args);
    }


    private UnAuthorizedException(ErrorType type, String message, Throwable cause, Object... args) {
        super(type, null, message, args);
        initCause(cause);
    }

    public UnAuthorizedException(String message, Throwable cause, Object... args) {
        super(TYPE, null, message, args);
        initCause(cause);
    }

    public static UnAuthorizedException of(Object... args) {
        return new UnAuthorizedException(TYPE.getId(), args);
    }

    public static UnAuthorizedException of(ErrorType type, Throwable cause) {
        return new UnAuthorizedException(type, cause.getMessage(), cause);
    }
    
    public static UnAuthorizedException of(ErrorType type, Throwable cause, Object... args) {
        return new UnAuthorizedException(type, cause.getMessage(), cause, args);
    }

}
