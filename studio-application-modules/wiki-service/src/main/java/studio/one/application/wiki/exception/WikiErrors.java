package studio.one.application.wiki.exception;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;

public final class WikiErrors {

    public static final ErrorType NOT_FOUND = ErrorType.of("error.wiki.not-found", HttpStatus.NOT_FOUND);
    public static final ErrorType CONFLICT = ErrorType.of("error.wiki.conflict", HttpStatus.CONFLICT);
    public static final ErrorType BAD_REQUEST = ErrorType.of("error.wiki.bad-request", HttpStatus.BAD_REQUEST);

    private WikiErrors() {
    }
}
