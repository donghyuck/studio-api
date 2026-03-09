package studio.one.application.mail.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MailSecretPresenceGuardTest {

    @Test
    void validateRejectsMissingImapPassword() {
        MailFeatureProperties properties = new MailFeatureProperties();
        properties.getImap().setHost("imap.example.com");
        properties.getImap().setUsername("user");
        properties.getImap().setPassword(" ");

        MailSecretPresenceGuard guard = new MailSecretPresenceGuard(properties);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsCompleteImapCredentials() {
        MailFeatureProperties properties = new MailFeatureProperties();
        properties.getImap().setHost("imap.example.com");
        properties.getImap().setUsername("user");
        properties.getImap().setPassword("secret");

        MailSecretPresenceGuard guard = new MailSecretPresenceGuard(properties);

        assertDoesNotThrow(guard::validate);
    }
}
