package studio.echo.platform.web.advice;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;

import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.web.dto.ProblemDetails;

@Slf4j
public abstract class AbstractExceptionHandler {

    protected static final MediaType PROBLEM_JSON = new MediaType("application", "problem+json", StandardCharsets.UTF_8);

    private static final Set<String> SENSITIVE_FIELDS = Set.of("password", "newPassword", "confirmPassword", "token", "refreshToken", "secret", "clientSecret");

    protected ProblemDetails.ProblemDetailsBuilder baseProblem(HttpStatus status, HttpServletRequest req) {
        String traceId = MDC.get("traceId");
        return ProblemDetails.builder()
                .type("about:blank")
                .title(status.getReasonPhrase())
                .status(status.value())
                .instance(req.getRequestURI())
                .traceId(traceId)
                .timestamp(OffsetDateTime.now())
                .locale(currentLocale());
    }

    protected ResponseEntity<ProblemDetails> withContentType(HttpStatus status, ProblemDetails body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(PROBLEM_JSON);
        return new ResponseEntity<>(body, h, status);
    }

        protected void logByStatus(HttpStatus status, Throwable ex, String code) {
        String traceId = MDC.get("traceId");
        String msg = "Handled exception: code={}, status={}, traceId={}";
        if (status.is5xxServerError()) { log.error(msg, code, status.value(), traceId, ex); return; }
        if (status==HttpStatus.UNAUTHORIZED || status==HttpStatus.FORBIDDEN) {
            log.warn(msg + ", reason={}", code, status.value(), traceId, ex.getMessage()); return;
        }
        if (status==HttpStatus.BAD_REQUEST || status==HttpStatus.UNPROCESSABLE_ENTITY ||
            status==HttpStatus.METHOD_NOT_ALLOWED || status==HttpStatus.NOT_ACCEPTABLE ||
            status==HttpStatus.CONFLICT) {
            log.info(msg + ", reason={}", code, status.value(), traceId, ex.getMessage()); return;
        }
        if (status.is4xxClientError()) { log.debug(msg + ", reason={}", code, status.value(), traceId, ex.getMessage()); return; }
        log.warn(msg + ", reason={}", code, status.value(), traceId, ex.getMessage());
    }

    protected ProblemDetails.Violation toViolation(FieldError err) {
        String field = err.getField();
        Object rejected = maskIfSensitive(field, err.getRejectedValue());
        return ProblemDetails.Violation.builder()
                .field(field)
                .rejectedValue(rejected)
                .code(firstOrNull(err.getCodes()))
                .message(err.getDefaultMessage())
                .build();
    }

    protected Object maskIfSensitive(String field, Object value) {
        if (value == null)
            return null;
        if (field == null)
            return value;
        if (SENSITIVE_FIELDS.contains(field))
            return "******";
        return value;
        // 필요 시 문자열에 "password" 포함 여부 등 heuristic 추가 가능
    }

    protected String firstOrNull(String[] codes) {
        return (codes != null && codes.length > 0) ? codes[0] : null;
    }

    protected String lastPath(String path) {
        if (path == null)
            return null;
        int i = path.lastIndexOf('.');
        return (i >= 0) ? path.substring(i + 1) : path;
    }

    protected Locale currentLocale() {
        try {
            return org.springframework.context.i18n.LocaleContextHolder.getLocale();
        } catch (Exception ignore) {
            return Locale.getDefault();
        }
    }

}
