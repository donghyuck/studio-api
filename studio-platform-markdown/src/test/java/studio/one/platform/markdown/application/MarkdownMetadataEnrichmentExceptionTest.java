package studio.one.platform.markdown.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MarkdownMetadataEnrichmentExceptionTest {

    @Test
    void classifiesUpstreamFailureWithoutLoggingProviderMessages() {
        IllegalStateException cause = new IllegalStateException("provider failure");

        MarkdownMetadataEnrichmentException exception =
                MarkdownMetadataEnrichmentException.upstreamUnavailable("chat-default", cause);

        assertThat(exception.getType().getId()).isEqualTo("error.markdown.metadata.upstream-unavailable");
        assertThat(exception.getType().getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.retryable()).isTrue();
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getMessage())
                .contains("chat-default", "causeType=java.lang.IllegalStateException")
                .doesNotContain("provider failure");
    }

    @Test
    void classifiesInvalidResponseSeparatelyFromModelConfiguration() {
        MarkdownMetadataEnrichmentException invalid =
                MarkdownMetadataEnrichmentException.invalidResponse("chat-default", null);
        MarkdownMetadataEnrichmentException configuration =
                MarkdownMetadataEnrichmentException.modelConfiguration("chat-default", null);

        assertThat(invalid.failure())
                .isEqualTo(MarkdownMetadataEnrichmentException.Failure.INVALID_RESPONSE);
        assertThat(invalid.getType().getId()).isEqualTo("error.markdown.metadata.invalid-response");
        assertThat(invalid.retryable()).isTrue();
        assertThat(configuration.getType().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(configuration.retryable()).isFalse();
    }
}
