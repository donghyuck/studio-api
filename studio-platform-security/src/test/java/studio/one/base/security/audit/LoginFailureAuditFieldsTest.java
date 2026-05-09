package studio.one.base.security.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LoginFailureAuditFieldsTest {

    @Test
    void truncatesAuditFieldsToSchemaLengths() {
        assertEquals(LoginFailureAuditFields.USERNAME_MAX_LENGTH,
                LoginFailureAuditFields.username("u".repeat(200)).length());
        assertEquals(LoginFailureAuditFields.USER_AGENT_MAX_LENGTH,
                LoginFailureAuditFields.userAgent("a".repeat(600)).length());
        assertEquals(LoginFailureAuditFields.FAILURE_TYPE_MAX_LENGTH,
                LoginFailureAuditFields.failureType("f".repeat(200)).length());
        assertEquals(LoginFailureAuditFields.MESSAGE_MAX_LENGTH,
                LoginFailureAuditFields.message("m".repeat(1200)).length());
    }

    @Test
    void keepsOptionalAuditFieldsNullable() {
        assertNull(LoginFailureAuditFields.userAgent(null));
        assertNull(LoginFailureAuditFields.failureType(null));
        assertNull(LoginFailureAuditFields.message(null));
        assertEquals("", LoginFailureAuditFields.username(null));
    }

    @Test
    void stripsControlCharactersBeforePersistenceOrLogging() {
        assertEquals("kim owner", LoginFailureAuditFields.username("kim\nowner"));
        assertEquals("JUnit  Agent", LoginFailureAuditFields.userAgent("JUnit\r\nAgent"));
        assertEquals("Bad Credentials", LoginFailureAuditFields.failureType("Bad\tCredentials"));
        assertEquals("line1 line2", LoginFailureAuditFields.message("line1\u0000line2"));
    }
}
