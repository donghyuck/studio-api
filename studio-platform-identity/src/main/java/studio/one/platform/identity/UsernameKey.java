package studio.one.platform.identity;

public record UsernameKey(String username) implements UserKey {
    public UsernameKey {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("username required");
    }
}