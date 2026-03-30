package studio.one.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserKeyTest {

    @Test
    void userIdKeyAcceptsPositiveValue() {
        UserIdKey key = new UserIdKey(1L);

        assertEquals(1L, key.userId());
    }

    @Test
    void userIdKeyRejectsNonPositiveValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new UserIdKey(0L));

        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void usernameKeyAcceptsNonBlankValue() {
        UsernameKey key = new UsernameKey("alice");

        assertEquals("alice", key.username());
    }

    @Test
    void usernameKeyRejectsBlankValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new UsernameKey(" "));

        assertTrue(exception.getMessage().contains("required"));
    }
}
