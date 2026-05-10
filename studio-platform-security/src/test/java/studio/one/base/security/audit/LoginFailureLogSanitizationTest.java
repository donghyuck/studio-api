package studio.one.base.security.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import studio.one.base.security.audit.domain.model.LoginFailureLog;

class LoginFailureLogSanitizationTest {

    @Test
    void sanitizeForPersistenceNormalizesSharedWriteFields() {
        LoginFailureLog log = LoginFailureLog.builder()
                .username("u".repeat(200))
                .remoteIp("::ffff:192.0.2.128")
                .userAgent("a".repeat(600))
                .failureType("f".repeat(200))
                .message("m".repeat(1200))
                .build();

        log.sanitizeForPersistence();

        assertEquals(LoginFailureAuditFields.USERNAME_MAX_LENGTH, log.getUsername().length());
        assertEquals("192.0.2.128", log.getRemoteIp());
        assertEquals(LoginFailureAuditFields.USER_AGENT_MAX_LENGTH, log.getUserAgent().length());
        assertEquals(LoginFailureAuditFields.FAILURE_TYPE_MAX_LENGTH, log.getFailureType().length());
        assertEquals(LoginFailureAuditFields.MESSAGE_MAX_LENGTH, log.getMessage().length());
    }

    @Test
    void sanitizeForPersistenceDropsInvalidIpAndUsesEmptyUsernameFallback() {
        LoginFailureLog log = LoginFailureLog.builder()
                .remoteIp("not-an-ip")
                .build();

        log.sanitizeForPersistence();

        assertEquals("", log.getUsername());
        assertNull(log.getRemoteIp());
    }
}
