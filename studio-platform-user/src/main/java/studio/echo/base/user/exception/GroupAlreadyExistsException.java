package studio.echo.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.error.ErrorType;
import studio.echo.platform.exception.AlreadyExistsException;

@SuppressWarnings({ "serial", "java:S110"}) 
public class GroupAlreadyExistsException extends AlreadyExistsException {


    private static final ErrorType BY_ID = ErrorType.of("error.group.already.exists.id", HttpStatus.INTERNAL_SERVER_ERROR);
    private static final ErrorType BY_NAME = ErrorType.of("error.group.already.exists.name", HttpStatus.INTERNAL_SERVER_ERROR);

    public GroupAlreadyExistsException(Long groupId) {
        super(BY_ID, "Group Already Exists", groupId);
    }

    public GroupAlreadyExistsException(String groupName) {
        super(BY_NAME, "Group Already Exists", groupName);
    }

    public static GroupAlreadyExistsException byId(Long groupId) {
        return new GroupAlreadyExistsException(groupId);
    }

    public static GroupAlreadyExistsException byName(String groupName) {
        return new GroupAlreadyExistsException(groupName);
    }
}
