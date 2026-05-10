package studio.one.base.security.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import studio.one.base.security.audit.domain.model.LoginFailureLog;
import studio.one.base.security.audit.domain.port.LoginFailureLogRepository;
import studio.one.base.security.authentication.lock.application.usecase.AccountLockService;

class LoginFailureEventListenerTest {

    @Test
    void savesNormalizedAndTruncatedAuditEntry() {
        LoginFailureLogRepository repository = mock(LoginFailureLogRepository.class);
        LoginFailureEventListener listener = new LoginFailureEventListener(repository);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "u".repeat(200), "bad-password");
        authentication.setDetails(new ClientRequestDetails(
                "::ffff:192.0.2.128",
                "198.51.100.10",
                "a".repeat(600),
                "session-id"));
        AuthenticationFailureBadCredentialsEvent event = new AuthenticationFailureBadCredentialsEvent(
                authentication,
                new BadCredentialsException("m".repeat(1200)));

        listener.onApplicationEvent(event);

        ArgumentCaptor<LoginFailureLog> log = ArgumentCaptor.forClass(LoginFailureLog.class);
        verify(repository).save(log.capture());
        LoginFailureLog saved = log.getValue();
        assertEquals(LoginFailureAuditFields.USERNAME_MAX_LENGTH, saved.getUsername().length());
        assertEquals("192.0.2.128", saved.getRemoteIp());
        assertEquals(LoginFailureAuditFields.USER_AGENT_MAX_LENGTH, saved.getUserAgent().length());
        assertEquals("BadCredentialsException", saved.getFailureType());
        assertEquals(LoginFailureAuditFields.MESSAGE_MAX_LENGTH, saved.getMessage().length());
    }

    @Test
    void invalidRemoteIpDoesNotSuppressAuditEntry() {
        LoginFailureLogRepository repository = mock(LoginFailureLogRepository.class);
        LoginFailureEventListener listener = new LoginFailureEventListener(repository);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "kim.owner", "bad-password");
        authentication.setDetails(new ClientRequestDetails(
                "not-an-ip",
                "not-an-ip",
                "JUnit",
                "session-id"));
        AuthenticationFailureBadCredentialsEvent event = new AuthenticationFailureBadCredentialsEvent(
                authentication,
                new BadCredentialsException("bad credentials"));

        listener.onApplicationEvent(event);

        ArgumentCaptor<LoginFailureLog> log = ArgumentCaptor.forClass(LoginFailureLog.class);
        verify(repository).save(log.capture());
        assertEquals("kim.owner", log.getValue().getUsername());
        assertNull(log.getValue().getRemoteIp());
    }

    @Test
    void repositoryFailureIsRecordedWithoutRunningAccountLockInAuditListener() {
        AccountLockService accountLockService = mock(AccountLockService.class);
        LoginFailureLogRepository repository = mock(LoginFailureLogRepository.class);
        when(repository.save(any(LoginFailureLog.class))).thenThrow(new IllegalStateException("db unavailable"));
        LoginFailureAuditFailureMonitor failureMonitor = new LoginFailureAuditFailureMonitor();
        LoginFailureEventListener listener = new LoginFailureEventListener(repository, failureMonitor);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "kim.owner", "bad-password");
        AuthenticationFailureBadCredentialsEvent event = new AuthenticationFailureBadCredentialsEvent(
                authentication,
                new BadCredentialsException("bad credentials"));

        listener.onApplicationEvent(event);

        assertEquals(1, failureMonitor.getFailureCount());
        assertEquals(IllegalStateException.class.getName(), failureMonitor.getLastErrorType());
        verifyNoInteractions(accountLockService);
    }

    @Test
    void rejectedExecutorDropsAuditWithoutBlockingAuthenticationFlow() {
        LoginFailureLogRepository repository = mock(LoginFailureLogRepository.class);
        LoginFailureAuditFailureMonitor failureMonitor = new LoginFailureAuditFailureMonitor();
        LoginFailureEventListener listener = new LoginFailureEventListener(
                repository, failureMonitor, task -> {
                    throw new RejectedExecutionException("full");
                }, null);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "kim.owner", "bad-password");
        AuthenticationFailureBadCredentialsEvent event = new AuthenticationFailureBadCredentialsEvent(
                authentication,
                new BadCredentialsException("bad credentials"));

        listener.onApplicationEvent(event);

        assertEquals(1, failureMonitor.getRejectedExecutionCount());
        assertEquals(1, failureMonitor.getDroppedExecutionCount());
        verifyNoInteractions(repository);
    }

    @Test
    void transactionFailureIsRecordedWithoutEscalatingToAuthenticationFlow() {
        LoginFailureLogRepository repository = mock(LoginFailureLogRepository.class);
        LoginFailureAuditFailureMonitor failureMonitor = new LoginFailureAuditFailureMonitor();
        LoginFailureEventListener listener = new LoginFailureEventListener(
                repository, failureMonitor, Runnable::run, failingTransactionOperations());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "kim.owner", "bad-password");
        AuthenticationFailureBadCredentialsEvent event = new AuthenticationFailureBadCredentialsEvent(
                authentication,
                new BadCredentialsException("bad credentials"));

        listener.onApplicationEvent(event);

        assertEquals(1, failureMonitor.getFailureCount());
        assertEquals(IllegalStateException.class.getName(), failureMonitor.getLastErrorType());
        verifyNoInteractions(repository);
    }

    private TransactionOperations failingTransactionOperations() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                throw new IllegalStateException("tx unavailable");
            }
        };
    }

    @Test
    void accountLockListenerRunsIndependentlyFromAuditRepositoryFailure() {
        AccountLockService accountLockService = mock(AccountLockService.class);
        LoginFailureLogRepository repository = mock(LoginFailureLogRepository.class);
        when(repository.save(any(LoginFailureLog.class))).thenThrow(new IllegalStateException("db unavailable"));
        LoginFailureAuditFailureMonitor failureMonitor = new LoginFailureAuditFailureMonitor();
        LoginFailureEventListener auditListener = new LoginFailureEventListener(repository, failureMonitor);
        AccountLockAuthenticationFailureListener lockListener =
                new AccountLockAuthenticationFailureListener(objectProvider(accountLockService));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "kim.owner", "bad-password");
        AuthenticationFailureBadCredentialsEvent event = new AuthenticationFailureBadCredentialsEvent(
                authentication,
                new BadCredentialsException("bad credentials"));

        auditListener.onApplicationEvent(event);
        lockListener.onApplicationEvent(event);

        assertEquals(1, failureMonitor.getFailureCount());
        verify(accountLockService).onFailedLogin("kim.owner");
    }

    private ObjectProvider<AccountLockService> objectProvider(AccountLockService accountLockService) {
        return new ObjectProvider<>() {
            @Override
            public AccountLockService getObject(Object... args) {
                return accountLockService;
            }

            @Override
            public AccountLockService getObject() {
                return accountLockService;
            }

            @Override
            public Iterator<AccountLockService> iterator() {
                return List.of(accountLockService).iterator();
            }
        };
    }
}
