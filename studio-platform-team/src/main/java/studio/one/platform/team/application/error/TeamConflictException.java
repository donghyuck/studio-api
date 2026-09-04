package studio.one.platform.team.application.error;

import studio.one.platform.exception.PlatformRuntimeException;

public class TeamConflictException extends PlatformRuntimeException {

    public TeamConflictException(String message, Object... args) {
        super(TeamErrors.CONFLICT, message, args);
    }
}
