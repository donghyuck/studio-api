package studio.one.application.wiki.application.error;

import studio.one.platform.exception.PlatformRuntimeException;

public class WikiValidationException extends PlatformRuntimeException {

    public WikiValidationException(String message, Object... args) {
        super(WikiErrors.BAD_REQUEST, message, args);
    }
}
