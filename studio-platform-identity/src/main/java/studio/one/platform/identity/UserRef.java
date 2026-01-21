package studio.one.platform.identity;

import java.util.Collections;
import java.util.Set;

public record UserRef(Long userId, String username, Set<String> roles) {
    public UserRef {
        roles = (roles == null) ? Collections.emptySet() : Set.copyOf(roles);
    }
}
