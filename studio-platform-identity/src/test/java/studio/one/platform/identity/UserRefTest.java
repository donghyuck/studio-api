package studio.one.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class UserRefTest {

    @Test
    void copiesRolesDefensively() {
        Set<String> roles = new LinkedHashSet<>();
        roles.add("ADMIN");

        UserRef userRef = new UserRef(1L, "alice", roles);
        roles.add("USER");

        assertEquals(Set.of("ADMIN"), userRef.roles());
    }

    @Test
    void replacesNullRolesWithEmptySet() {
        UserRef userRef = new UserRef(1L, "alice", null);

        assertTrue(userRef.roles().isEmpty());
    }
}
