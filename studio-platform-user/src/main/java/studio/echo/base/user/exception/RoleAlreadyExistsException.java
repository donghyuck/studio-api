package studio.echo.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.error.ErrorType;
import studio.echo.platform.exception.AlreadyExistsException;

@SuppressWarnings({ "serial", "java:S110"}) 
public class RoleAlreadyExistsException extends AlreadyExistsException {

    private static final ErrorType BY_ID = ErrorType.of("error.role.already.exists.id", HttpStatus.INTERNAL_SERVER_ERROR);
    private static final ErrorType BY_NAME = ErrorType.of("error.role.already.exists.name", HttpStatus.INTERNAL_SERVER_ERROR);

    public RoleAlreadyExistsException(Long roleId) {
        super(BY_ID, "Role Already Exists", roleId);
    }

    public RoleAlreadyExistsException(String roleName) {
        super(BY_NAME, "Role Already Exists", roleName);
    }

    public static RoleAlreadyExistsException byId(Long roleId) {
        return new RoleAlreadyExistsException(roleId);
    }

    public static RoleAlreadyExistsException byName(String roleName) {
        return new RoleAlreadyExistsException(roleName);
    }

}
