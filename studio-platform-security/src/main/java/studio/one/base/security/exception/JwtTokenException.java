package studio.one.base.security.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.PlatformException;

public class JwtTokenException extends PlatformException {

    private static final ErrorType BY_JWT_TOKEN_EXPIRED = ErrorType.of("error.security.jwt.expired",
            HttpStatus.UNAUTHORIZED);
    private static final ErrorType BY_JWT_TOKEN_INVALID = ErrorType.of("error.security.jwt.invalid",
            HttpStatus.UNAUTHORIZED);

    public JwtTokenException(ErrorType type, String message, Object... args) {
        super(type, message, args);
    }

    public static JwtTokenException expired(String token) {
        return new JwtTokenException(BY_JWT_TOKEN_EXPIRED, "JWT Token Expired", token);
    }
    public static JwtTokenException invalid(String token) {
        return new JwtTokenException(BY_JWT_TOKEN_INVALID, "JWT Token Invalid", token);
    }
}
