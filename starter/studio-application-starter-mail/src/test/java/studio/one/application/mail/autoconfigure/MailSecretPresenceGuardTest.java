package studio.one.application.mail.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import studio.one.application.mail.config.ImapProperties;

class MailSecretPresenceGuardTest {

    @Test
    void validateRejectsMissingImapPassword() {
        ImapProperties properties = new ImapProperties();
        properties.setHost("imap.example.com");
        properties.setUsername("user");
        properties.setPassword(" ");

        MailSecretPresenceGuard guard = new MailSecretPresenceGuard(properties);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsCompleteImapCredentials() {
        ImapProperties properties = new ImapProperties();
        properties.setHost("imap.example.com");
        properties.setUsername("user");
        properties.setPassword("secret");

        MailSecretPresenceGuard guard = new MailSecretPresenceGuard(properties);

        assertDoesNotThrow(guard::validate);
    }
}
