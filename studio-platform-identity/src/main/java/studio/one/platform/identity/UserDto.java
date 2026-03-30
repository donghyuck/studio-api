package studio.one.platform.identity;

/**
 * Lightweight user projection for response payloads that only need id and username.
 *
 * <p>Unlike {@link UserRef}, this type intentionally omits roles.</p>
 */
public record UserDto(Long userId, String username) {}
