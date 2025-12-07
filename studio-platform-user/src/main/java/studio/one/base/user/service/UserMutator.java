package studio.one.base.user.service;

import java.time.Instant;

import studio.one.base.user.domain.model.User;

/**
 * Encapsulates mutations on a User implementation so that the service layer
 * does not need concrete entity setters.
 */
public interface UserMutator<T extends User> {

    default void prepareForCreate(T user) {
        // no-op by default
    }

    default String getPassword(T user) {
        return null;
    }

    default void setPassword(T user, String encoded) {
        // no-op by default
    }

    default boolean isEnabled(T user) {
        return user.isEnabled();
    }

    default void setEnabled(T user, boolean enabled) {
        // no-op by default
    }

    default int getFailedAttempts(T user) {
        return user.getFailedAttempts();
    }

    default void setFailedAttempts(T user, int attempts) {
        // no-op by default
    }

    default Instant getLastFailedAt(T user) {
        return user.getLastFailedAt();
    }

    default void setLastFailedAt(T user, Instant at) {
        // no-op by default
    }

    default Instant getAccountLockedUntil(T user) {
        return user.getAccountLockedUntil();
    }

    default void setAccountLockedUntil(T user, Instant until) {
        // no-op by default
    }
}
