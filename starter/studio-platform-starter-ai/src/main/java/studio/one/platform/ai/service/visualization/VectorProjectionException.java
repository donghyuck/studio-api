package studio.one.platform.ai.service.visualization;

import org.springframework.http.HttpStatus;

public class VectorProjectionException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Long totalCount;
    private final Integer maxAllowed;
    private final Integer sampleSize;

    public VectorProjectionException(
            HttpStatus status,
            String code,
            String message,
            Long totalCount,
            Integer maxAllowed,
            Integer sampleSize) {
        super(message);
        this.status = status;
        this.code = code;
        this.totalCount = totalCount;
        this.maxAllowed = maxAllowed;
        this.sampleSize = sampleSize;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Long totalCount() {
        return totalCount;
    }

    public Integer maxAllowed() {
        return maxAllowed;
    }

    public Integer sampleSize() {
        return sampleSize;
    }
}
