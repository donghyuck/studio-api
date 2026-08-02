package studio.one.application.webknowledge.infrastructure.web;

public final class WebPageFetchException extends RuntimeException {

    private final String errorCode;

    public WebPageFetchException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public WebPageFetchException(String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
