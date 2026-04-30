package studio.one.platform.ai.web.controller;

import java.time.OffsetDateTime;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.web.dto.ProblemDetails;

@RestControllerAdvice(assignableTypes = {
        ChatController.class,
        EmbeddingController.class,
        VectorController.class,
        VectorVisualizationMgmtController.class,
        RagController.class,
        RagChunkPreviewController.class,
        RagIndexJobController.class,
        QueryRewriteController.class,
        AiInfoController.class
})
@Slf4j
public class AiWebExceptionHandler {

    private static final String GOOGLE_GENAI_CLIENT_EXCEPTION = "com.google.genai.errors.ClientException";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetails> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ProblemDetails body = ProblemDetails.builder()
                .type("about:blank")
                .title(status.getReasonPhrase())
                .status(status.value())
                .detail(ex.getReason())
                .instance(request.getRequestURI())
                .timestamp(OffsetDateTime.now())
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetails> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {
        if (isGoogleGenAiQuotaExceeded(ex)) {
            log.warn("AI provider quota exceeded. provider={}", rootCause(ex).getClass().getSimpleName());
            return problem(HttpStatus.TOO_MANY_REQUESTS,
                    "AI provider quota exceeded. Please retry later or check provider quota.",
                    request);
        }
        // Keep non-AI-provider runtime failures on the application's existing global exception path.
        throw ex;
    }

    private ResponseEntity<ProblemDetails> problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetails body = ProblemDetails.builder()
                .type("about:blank")
                .title(status.getReasonPhrase())
                .status(status.value())
                .detail(detail)
                .instance(request.getRequestURI())
                .timestamp(OffsetDateTime.now())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private boolean isGoogleGenAiQuotaExceeded(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (isGoogleGenAiClientException(current)
                    && containsQuotaSignal(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isGoogleGenAiClientException(Throwable ex) {
        return GOOGLE_GENAI_CLIENT_EXCEPTION.equals(ex.getClass().getName());
    }

    private boolean containsQuotaSignal(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("quota")
                || lower.contains("rate limit")
                || lower.contains("resource_exhausted")
                || lower.contains("too_many_requests")
                || (lower.contains("429") && lower.contains("rate"));
    }

    private Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

}
