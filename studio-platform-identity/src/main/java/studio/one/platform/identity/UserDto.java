package studio.one.platform.identity;

import java.util.Objects;

/**
 * Lightweight user projection for response payloads that only need id and username.
 *
 * <p>Unlike {@link UserRef}, this type intentionally omits roles.</p>
 */
public final class UserDto {
    private final Long userId;
    private final String username;

    public UserDto(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Long userId() {
        return userId;
    }

    public Long getUserId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserDto)) {
            return false;
        }
        UserDto userDto = (UserDto) o;
        return Objects.equals(userId, userDto.userId)
                && Objects.equals(username, userDto.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username);
    }

    @Override
    public String toString() {
        return "UserDto[userId=" + userId + ", username=" + username + "]";
    }
}
