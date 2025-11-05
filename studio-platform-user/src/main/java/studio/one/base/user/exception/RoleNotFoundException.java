package studio.one.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.NotFoundException;

@SuppressWarnings({ "serial", "java:S110"}) 
public class RoleNotFoundException extends NotFoundException {

    private static final ErrorType BY_ID = ErrorType.of("error.role.not.found.id", HttpStatus.NOT_FOUND);
    private static final ErrorType BY_NAME = ErrorType.of("error.role.not.found.name", HttpStatus.NOT_FOUND);

    public RoleNotFoundException(Long roleId) {
        super(BY_ID, "Role Not Found", roleId);
    }

    public RoleNotFoundException(String roleName) {
        super(BY_NAME, "Role Not Found", roleName);
    }

    public static RoleNotFoundException byId(Long roleId) {
        return new RoleNotFoundException(roleId);
    }

    public static RoleNotFoundException byName(String roleName) {
        return new RoleNotFoundException(roleName);
    }

}
