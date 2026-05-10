package studio.one.application.wiki.application.error;

import studio.one.platform.exception.PlatformRuntimeException;

public class WikiConflictException extends PlatformRuntimeException {

    public WikiConflictException(String message, Object... args) {
        super(WikiErrors.CONFLICT, message, args);
    }
}
