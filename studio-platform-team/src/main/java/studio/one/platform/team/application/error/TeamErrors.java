package studio.one.platform.team.application.error;

import org.springframework.http.HttpStatus;
import studio.one.platform.error.ErrorType;

public final class TeamErrors {
    public static final ErrorType NOT_FOUND = ErrorType.of("error.team.not-found", HttpStatus.NOT_FOUND);
    public static final ErrorType CONFLICT = ErrorType.of("error.team.conflict", HttpStatus.CONFLICT);
    public static final ErrorType BAD_REQUEST = ErrorType.of("error.team.bad-request", HttpStatus.BAD_REQUEST);

    private TeamErrors() {
    }
}
