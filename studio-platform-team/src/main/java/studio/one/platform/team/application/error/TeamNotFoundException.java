package studio.one.platform.team.application.error;

import studio.one.platform.exception.PlatformRuntimeException;

public class TeamNotFoundException extends PlatformRuntimeException {

    public TeamNotFoundException(String message, Object... args) {
        super(TeamErrors.NOT_FOUND, message, args);
    }
}
