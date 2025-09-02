package studio.echo.base.security.web.adivce;

import javax.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
import studio.echo.platform.web.advice.AbstractExceptionHandler;
import studio.echo.platform.web.dto.ProblemDetails;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class SecurityExceptionHandler extends AbstractExceptionHandler {

    private final I18n i18n;

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetails> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String code = "error.security.unauthorized";
        if (ex instanceof BadCredentialsException || ex instanceof UsernameNotFoundException) {
            code = "error.security.auth.bad-credentials"; // 401
        } else if (ex instanceof LockedException) {
            status = HttpStatus.FORBIDDEN; // 403
            code = "error.security.auth.account-locked";
        } else if (ex instanceof DisabledException) {
            status = HttpStatus.FORBIDDEN;
            code = "error.security.auth.account-disabled";
        } else if (ex instanceof AccountExpiredException) {
            status = HttpStatus.FORBIDDEN;
            code = "error.security.auth.account-expired";
        } else if (ex instanceof CredentialsExpiredException) {
            status = HttpStatus.FORBIDDEN;
            code = "error.security.auth.credentials-expired";
        }
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:security.auth")
                .detail(I18nUtils.safeGet(i18n, code, "Authentication failed"))
                .code(code)
                .build();
        logByStatus(status, ex, code);
        return withContentType(status, body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetails> handleAccessDenied(AccessDeniedException ex,
            HttpServletRequest req) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        String code = "error.security.access.denied";
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:security.access-denied")
                .detail(I18nUtils.safeGet(i18n, code, "Access denied"))
                .code(code)
                .build();
        logByStatus(status, ex, code);
        return withContentType(status, body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetails> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest req) {
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        String code = "error.request.method.not-allowed";
        ProblemDetails body = baseProblem(status, req)
                .type("urn:error:request.method-not-allowed")
                .detail(i18n.get(code, "HTTP method not allowed"))
                .code(code)
                .build();
        logByStatus(status, ex, code);
        return withContentType(status, body);
    }

}
