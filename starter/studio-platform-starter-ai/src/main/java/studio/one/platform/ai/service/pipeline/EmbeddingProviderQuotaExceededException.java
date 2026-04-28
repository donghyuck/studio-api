package studio.one.platform.ai.service.pipeline;

public class EmbeddingProviderQuotaExceededException extends RuntimeException {

    public EmbeddingProviderQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
