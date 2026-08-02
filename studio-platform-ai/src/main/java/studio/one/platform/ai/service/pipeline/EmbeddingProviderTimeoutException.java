package studio.one.platform.ai.service.pipeline;

public class EmbeddingProviderTimeoutException extends RuntimeException {

    public EmbeddingProviderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
