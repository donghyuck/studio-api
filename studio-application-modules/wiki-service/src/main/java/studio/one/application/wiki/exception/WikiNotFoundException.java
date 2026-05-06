package studio.one.application.wiki.exception;

import studio.one.platform.exception.PlatformRuntimeException;

public class WikiNotFoundException extends PlatformRuntimeException {

    public WikiNotFoundException(String message, Object... args) {
        super(WikiErrors.NOT_FOUND, message, args);
    }
}
