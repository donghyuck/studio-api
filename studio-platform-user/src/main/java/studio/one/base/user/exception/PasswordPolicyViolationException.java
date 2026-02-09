package studio.one.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.PlatformException;

@SuppressWarnings({ "serial", "java:S110"})
public class PasswordPolicyViolationException extends PlatformException {

    private static final ErrorType POLICY_VIOLATION = ErrorType.of("error.user.password.policy", HttpStatus.BAD_REQUEST);

    public PasswordPolicyViolationException(String reason) {
        super(POLICY_VIOLATION, "Password policy violation", reason);
    }

    public static PasswordPolicyViolationException of(String reason) {
        return new PasswordPolicyViolationException(reason);
    }
}
