package studio.one.platform.team.application.error;

import studio.one.platform.exception.PlatformRuntimeException;

public class TeamValidationException extends PlatformRuntimeException {

    public TeamValidationException(String message, Object... args) {
        super(TeamErrors.BAD_REQUEST, message, args);
    }
}
