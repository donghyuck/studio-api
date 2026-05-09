package studio.one.base.security.audit;

import org.springframework.context.ApplicationListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.authentication.lock.service.AccountLockService;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class AccountLockAuthenticationFailureListener
        implements ApplicationListener<AuthenticationFailureBadCredentialsEvent>, Ordered {

    private final ObjectProvider<AccountLockService> accountLockService;

    public AccountLockAuthenticationFailureListener(ObjectProvider<AccountLockService> accountLockService) {
        this.accountLockService = accountLockService;
    }

    @Override
    public void onApplicationEvent(AuthenticationFailureBadCredentialsEvent event) {
        LoginFailureAuditEventData auditEvent = LoginFailureAuditEventDataExtractor.extract(event);
        try {
            accountLockService.ifAvailable(service -> service.onFailedLogin(auditEvent.username()));
        } catch (Exception ex) {
            log.warn("Account lock update failed (ignored). username={}, reason={}", auditEvent.username(),
                    ex.toString());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
