package studio.one.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.AlreadyExistsException;

@SuppressWarnings({ "serial", "java:S110"}) 
public class UserAlreadyExistsException  extends AlreadyExistsException {

    private static final ErrorType BY_ID = ErrorType.of("error.user.already.exists.id", HttpStatus.INTERNAL_SERVER_ERROR);
    private static final ErrorType BY_NAME = ErrorType.of("error.user.already.exists.username", HttpStatus.INTERNAL_SERVER_ERROR);
    private static final ErrorType BY_EMAIL = ErrorType.of("error.user.already.exists.email", HttpStatus.CONFLICT);

    public UserAlreadyExistsException(Long userId) {
        super(BY_ID, "User Already Exists", userId);
    }

    public UserAlreadyExistsException(String userName) {
        super(BY_NAME, "User Already Exists", userName);
    }

    public UserAlreadyExistsException(ErrorType type, String value) {
        super(type, "User Already Exists", value);
    }

    public static UserAlreadyExistsException byId(Long userId) {
        return new UserAlreadyExistsException(userId);
    }

    public static UserAlreadyExistsException byName(String userName) {
        return new UserAlreadyExistsException(userName);
    }

    public static UserAlreadyExistsException byEmail(String email) {
        return new UserAlreadyExistsException(BY_EMAIL, email);
    }

}
