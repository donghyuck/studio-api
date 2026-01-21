package studio.one.platform.identity;

public record UserIdKey(long userId) implements UserKey {
    public UserIdKey {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }
}
