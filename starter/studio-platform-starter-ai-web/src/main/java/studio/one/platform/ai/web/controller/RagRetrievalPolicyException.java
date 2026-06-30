package studio.one.platform.ai.web.controller;

import org.springframework.http.HttpStatus;

public class RagRetrievalPolicyException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public RagRetrievalPolicyException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
