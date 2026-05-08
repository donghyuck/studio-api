package studio.one.base.security.audit;

import java.time.Instant;

import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LoginFailureAuditEventDataExtractor {

    private LoginFailureAuditEventDataExtractor() {
    }

    public static LoginFailureAuditEventData extract(AuthenticationFailureBadCredentialsEvent event) {
        Instant now = Instant.now();
        String username = null;
        String remoteIp = null;
        String userAgent = null;

        try {
            if (event.getAuthentication() != null) {
                username = event.getAuthentication().getName();
                Object details = event.getAuthentication().getDetails();
                if (details instanceof ClientRequestDetails crd) {
                    remoteIp = crd.getRemoteIp();
                    userAgent = crd.getUserAgent();
                } else if (details instanceof WebAuthenticationDetails wad) {
                    remoteIp = wad.getRemoteAddress();
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to extract client details: {}", ex.toString(), ex);
        }

        return new LoginFailureAuditEventData(
                LoginFailureAuditFields.username(username),
                IpAddressLiterals.normalizeOrNull(remoteIp),
                LoginFailureAuditFields.userAgent(userAgent),
                LoginFailureAuditFields.failureType(
                        event.getException() != null ? event.getException().getClass().getSimpleName() : null),
                LoginFailureAuditFields.message(
                        event.getException() != null ? event.getException().getMessage() : null),
                now);
    }
}
