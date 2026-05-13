package studio.one.platform.identity;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class UserRef {
    private final Long userId;
    private final String username;
    private final Set<String> roles;

    public UserRef(Long userId, String username, Set<String> roles) {
        this.userId = userId;
        this.username = username;
        this.roles = (roles == null) ? Collections.emptySet() : Set.copyOf(roles);
    }

    public Long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public Set<String> roles() {
        return roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRef)) {
            return false;
        }
        UserRef userRef = (UserRef) o;
        return Objects.equals(userId, userRef.userId)
                && Objects.equals(username, userRef.username)
                && Objects.equals(roles, userRef.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username, roles);
    }

    @Override
    public String toString() {
        return "UserRef[userId=" + userId + ", username=" + username + ", roles=" + roles + "]";
    }
}
