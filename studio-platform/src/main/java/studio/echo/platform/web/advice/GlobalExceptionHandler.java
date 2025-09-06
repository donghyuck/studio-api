package studio.echo.platform.web.advice;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.error.ErrorType;
import studio.echo.platform.exception.PlatformException;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
import studio.echo.platform.web.dto.ProblemDetails;

/**
 * A global exception handler for the application that handles various types of
 * exceptions and returns a standardized {@link ProblemDetails} response.
 *
 * @author donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends AbstractExceptionHandler {

    private final I18n i18n;

    /**
     * Handles business domain errors.
     * 
     * @param ex  the exception
     * @param req the current HTTP request
     * @return a {@code ResponseEntity} with the problem details
     */
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ProblemDetails> handlePlatform(PlatformException ex, HttpServletRequest req) {
        ErrorType type = ex.getType();
        HttpStatus status = (type.getStatus() != null) ? type.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        String code = type.getId(); // i18n key (ex: error.user.notFound.id)
        String detail = I18nUtils.safeGet(i18n, code, ex.getArgs());
        String traceId = MDC.get("traceId");
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:" + code)
                .detail(detail)
                .traceId(traceId)
                .code(code)
                .build();
        logByStatus(status, ex, code);
        return withContentType(status, body);
    }

    /**
     * Handles bean validation errors for {@code @RequestBody} DTOs.
     * 
     * @param ex  the exception
     * @param req the current HTTP request
     * @return a {@code ResponseEntity} with the problem details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {

        BindingResult br = ex.getBindingResult();
        List<ProblemDetails.Violation> violations = br.getFieldErrors().stream()
                .map(this::toViolation)
                .collect(Collectors.toList());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:validation")
                .detail(I18nUtils.safeGet(i18n, "error.validation", "Validation failed"))
                .violations(violations) // <- 필드명 일치
                .build();
        log.debug("Validation failed: {}", violations);
        return withContentType(status, body);
    }

    /**
     * Handles bean validation errors for {@code @ModelAttribute} or form data.
     *
     * @param ex  the exception
     * @param req the current HTTP request
     * @return a {@code ResponseEntity} with the problem details
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetails> handleBindException(BindException ex, HttpServletRequest req) {
        List<ProblemDetails.Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .collect(Collectors.toList());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:validation")
                .detail(I18nUtils.safeGet(i18n, "error.validation", "Validation failed"))
                .violations(violations)
                .build();
        log.debug("Binding failed: {}", violations);
        return withContentType(status, body);
    }

    /**
     * Handles bean validation errors for {@code @RequestParam} or
     * {@code @PathVariable}.
     *
     * @param ex  the exception
     * @param req the current HTTP request
     * @return a {@code ResponseEntity} with the problem details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetails> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest req) {
        List<ProblemDetails.Violation> violations = ex.getConstraintViolations().stream()
                .map(v -> ProblemDetails.Violation.builder()
                        .field(lastPath(v.getPropertyPath().toString()))
                        .rejectedValue(maskIfSensitive(lastPath(v.getPropertyPath().toString()), v.getInvalidValue()))
                        .code(v.getConstraintDescriptor() != null
                                ? v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                                : null)
                        .message(v.getMessage())
                        .build())
                .collect(Collectors.toList());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:validation")
                .detail(I18nUtils.safeGet(i18n, "error.validation", "Validation failed"))
                .violations(violations)
                .build();
        log.debug("Constraint violation: {}", violations);
        return withContentType(status, body);
    }

    /**
     * Handles errors from parsing or missing request bodies.
     *
     * @param ex  the exception
     * @param req the current HTTP request
     * @return a {@code ResponseEntity} with the problem details
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleNotReadable(HttpMessageNotReadableException ex,
            HttpServletRequest req) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:request-body")
                .detail(I18nUtils.safeGet(i18n, "error.request.malformed", "Request body is missing or malformed."))
                .build();

        if (isBodyMissing(ex, req)) {
            String hint = I18nUtils.safeGet(i18n, "error.request.body.missing.hint");
            log.debug(i18n.get("error.request.body.missing", hint));
        } else {
            log.debug("Malformed request body: {}", ex.getMessage());
        }

        return withContentType(status, body);
    }

    private boolean isBodyMissing(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("Required request body is missing") ||
                msg.contains("No content to map") || // Jackson
                msg.contains("HTTP message body is missing"))) { // 일부 변종
            return true;
        }
        if (req instanceof ContentCachingRequestWrapper) {
            byte[] buf = ((ContentCachingRequestWrapper) req).getContentAsByteArray();
            if (buf == null || buf.length == 0)
                return true;
        }
        String ct = req.getContentType();
        boolean json = ct != null && ct.toLowerCase(Locale.ROOT).contains("json");
        if (json) {
            long len = req.getContentLengthLong(); // -1: 모름(주로 chunked)
            if (len == 0)
                return true;
            String cl = req.getHeader("Content-Length");
            if (cl != null) {
                try {
                    if (Long.parseLong(cl.trim()) == 0)
                        return true;
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return false;
    }

    /**
     * Handles HTTP-level errors like unsupported methods or media types.
     *
     * @param ex  the exception
     * @param req the current HTTP request
     * @return a {@code ResponseEntity} with the problem details
     */
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ProblemDetails> handleHttp4xx(Exception ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:http")
                .detail(ex.getMessage())
                .build();
        log.debug("HTTP 4xx: {}", ex.toString());
        return withContentType(status, body);
    }

    /**
     * Handles any other unhandled exceptions.
     *
     * @param ex  the exception
     * @param req the current HTTP request
     * @return a {@code ResponseEntity} with the problem details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleOthers(Exception ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:unexpected")
                .detail(I18nUtils.safeGet(i18n, "error.unexpected", "Unexpected error"))
                .build();
        log.error("Unhandled exception", ex);
        return withContentType(status, body);
    }
 
}
