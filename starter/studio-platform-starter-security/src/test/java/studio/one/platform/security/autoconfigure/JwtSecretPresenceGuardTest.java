package studio.one.platform.security.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtSecretPresenceGuardTest {

    @Test
    void validateRejectsMissingSecret() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret(" ");

        JwtSecretPresenceGuard guard = new JwtSecretPresenceGuard(properties);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsConfiguredSecret() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("test-secret");

        JwtSecretPresenceGuard guard = new JwtSecretPresenceGuard(properties);

        assertDoesNotThrow(guard::validate);
    }
}
