package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AiWebExceptionHandlerTest {

    @Test
    void mapsResponseStatusExceptionToProblemDetailsStatus() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/ai/chat");

        var response = new AiWebExceptionHandler().handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown AI provider: missing"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody().getDetail()).isEqualTo("Unknown AI provider: missing");
        assertThat(response.getBody().getInstance()).isEqualTo("/api/ai/chat");
    }

    @Test
    void mapsGoogleGenAiQuotaExceptionToTooManyRequests() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/ai/chat");

        RuntimeException ex = new RuntimeException("Failed to generate content",
                new com.google.genai.errors.ClientException(
                        "RESOURCE_EXHAUSTED: Quota exceeded for generate_content_free_tier_requests"));

        var response = new AiWebExceptionHandler().handleRuntimeException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getBody().getDetail()).isEqualTo(
                "AI provider quota exceeded. Please retry later or check provider quota.");
        assertThat(response.getBody().getInstance()).isEqualTo("/api/ai/chat");
    }

    @Test
    void rethrowsQuotaMessageFromUnexpectedExceptionClass() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/ai/chat");
        RuntimeException ex = new RuntimeException("Failed to generate content",
                new RuntimeException("RESOURCE_EXHAUSTED: Quota exceeded"));

        org.junit.jupiter.api.Assertions.assertSame(ex,
                org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                        () -> new AiWebExceptionHandler().handleRuntimeException(ex, request)));
    }

    @Test
    void rethrowsUnknownRuntimeException() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/ai/chat");
        RuntimeException ex = new RuntimeException("unexpected");

        org.junit.jupiter.api.Assertions.assertSame(ex,
                org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                        () -> new AiWebExceptionHandler().handleRuntimeException(ex, request)));
    }

}
