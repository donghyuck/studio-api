package studio.one.platform.markdown.application;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.PlatformRuntimeException;

/**
 * Safe, classified failure raised by document metadata LLM enrichment.
 */
public final class MarkdownMetadataEnrichmentException extends PlatformRuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Failure {
        MODEL_CONFIGURATION(
                ErrorType.of("error.markdown.metadata.model-configuration", HttpStatus.SERVICE_UNAVAILABLE),
                false),
        UPSTREAM_UNAVAILABLE(
                ErrorType.of("error.markdown.metadata.upstream-unavailable", HttpStatus.BAD_GATEWAY),
                true),
        INVALID_RESPONSE(
                ErrorType.of("error.markdown.metadata.invalid-response", HttpStatus.BAD_GATEWAY),
                true);

        private final ErrorType errorType;
        private final boolean retryable;

        Failure(ErrorType errorType, boolean retryable) {
            this.errorType = errorType;
            this.retryable = retryable;
        }
    }

    private final Failure failure;

    private MarkdownMetadataEnrichmentException(Failure failure, String logMessage, Throwable cause) {
        super(failure.errorType, logMessage + causeType(cause));
        this.failure = failure;
    }

    public static MarkdownMetadataEnrichmentException modelConfiguration(String deploymentId, Throwable cause) {
        return new MarkdownMetadataEnrichmentException(
                Failure.MODEL_CONFIGURATION,
                "Markdown metadata model deployment is unavailable: " + safe(deploymentId),
                cause);
    }

    public static MarkdownMetadataEnrichmentException upstreamUnavailable(String deploymentId, Throwable cause) {
        return new MarkdownMetadataEnrichmentException(
                Failure.UPSTREAM_UNAVAILABLE,
                "Markdown metadata model call failed: " + safe(deploymentId),
                cause);
    }

    public static MarkdownMetadataEnrichmentException invalidResponse(String deploymentId, Throwable cause) {
        return new MarkdownMetadataEnrichmentException(
                Failure.INVALID_RESPONSE,
                "Markdown metadata model returned an invalid response: " + safe(deploymentId),
                cause);
    }

    public Failure failure() {
        return failure;
    }

    public boolean retryable() {
        return failure.retryable;
    }

    private static String safe(String deploymentId) {
        return deploymentId == null || deploymentId.isBlank() ? "<unconfigured>" : deploymentId;
    }

    private static String causeType(Throwable cause) {
        return cause == null ? "" : "; causeType=" + cause.getClass().getName();
    }
}
