package studio.echo.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.error.ErrorType;
import studio.echo.platform.exception.NotFoundException;

@SuppressWarnings({ "serial", "java:S110"}) 
public class UserNotFoundException extends NotFoundException {

    private static final ErrorType BY_ID = ErrorType.of("error.user.not.found.id", HttpStatus.NOT_FOUND);
    private static final ErrorType BY_EMAIL = ErrorType.of("error.user.not.found.email", HttpStatus.NOT_FOUND);
    private static final ErrorType BY_NAME = ErrorType.of("error.user.not.found.username", HttpStatus.NOT_FOUND);
    private static final String DEFAULT_MESSAGE = "User Not Found";
    
    public UserNotFoundException(Long userId) {
        super(BY_ID, DEFAULT_MESSAGE, userId);
    }

    public UserNotFoundException(String email) {
        super(BY_EMAIL, DEFAULT_MESSAGE, email);
    }
    public UserNotFoundException(ErrorType type , String val) {
        super(type, DEFAULT_MESSAGE, val);
    }

    public static UserNotFoundException byId(Long userId) {
        return new UserNotFoundException(userId);
    }

    public static UserNotFoundException byEmail(String email) {
        return new UserNotFoundException(email);
    }

    public static UserNotFoundException of(String name) {
        return new UserNotFoundException(BY_NAME, name);
    }
}
