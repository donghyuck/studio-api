package studio.one.platform.identity;

import java.util.Objects;

public final class UsernameKey implements UserKey {
    private final String username;

    public UsernameKey(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username required");
        }
        this.username = username;
    }

    public String username() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsernameKey)) {
            return false;
        }
        UsernameKey that = (UsernameKey) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "UsernameKey[username=" + username + "]";
    }
}
