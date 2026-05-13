package studio.one.platform.identity;

public final class UserIdKey implements UserKey {
    private final long userId;

    public UserIdKey(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
    }

    public long userId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserIdKey)) {
            return false;
        }
        UserIdKey userIdKey = (UserIdKey) o;
        return userId == userIdKey.userId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(userId);
    }

    @Override
    public String toString() {
        return "UserIdKey[userId=" + userId + "]";
    }
}
