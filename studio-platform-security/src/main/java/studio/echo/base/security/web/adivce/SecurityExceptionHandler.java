package studio.echo.base.security.web.adivce;

import java.time.Clock;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
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
import studio.echo.base.security.authentication.AccountLockService;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
import studio.echo.platform.web.advice.AbstractExceptionHandler;
import studio.echo.platform.web.dto.ProblemDetails;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class SecurityExceptionHandler extends AbstractExceptionHandler {

    private final ObjectProvider<AccountLockService> accountLockService;
    private final Clock clock;
    private final I18n i18n;

    private String resolveUsername(HttpServletRequest req) {
        Object attr = req.getAttribute("login.username");
        if (attr instanceof String && !((String) attr).isBlank()) {
            return (String) attr;
        }
        String param = req.getParameter("username"); // form-login인 경우
        return (param != null && !param.isBlank()) ? param : null;
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ProblemDetails> handleLocked(LockedException ex, HttpServletRequest req) {

        String username = resolveUsername(req); // 아래 유틸 참고
        long remainingSec = 0L;
        java.time.Instant until = null;
        AccountLockService svc = accountLockService.getIfAvailable();
        String detail = ex.getMessage(); 
        final String code = "error.security.auth.account-locked"; 
        if (svc != null && username != null && !username.isBlank()) {
            try {
                remainingSec = Math.max(0L, svc.getRemainingLockSeconds(username, clock));
                until = svc.getLockedUntil(username).orElse(null);
                long remainingMin = (remainingSec + 59) / 60;
                detail = I18nUtils.safeGet(i18n, code + ".minutes", remainingMin);
            } catch (Exception ignore) { 
                detail = I18nUtils.safeGet(i18n, code, "Your account is locked.");
            }
        }
        
        ProblemDetails body = baseProblem(HttpStatus.LOCKED, req)
                .type("urn:error:security.auth.account-locked")
                .detail(detail)
                .code(code)
                .build();

        return ResponseEntity.status(HttpStatus.LOCKED)
                .header("X-Account-Locked-Until", until != null ? until.toString() : "")
                .header("X-Account-Lock-Remaining-Seconds", String.valueOf(remainingSec))
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetails> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String code = "error.security.unauthorized";
        if (ex instanceof BadCredentialsException || ex instanceof UsernameNotFoundException) {
            code = "error.security.auth.bad-credentials"; // 401
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
