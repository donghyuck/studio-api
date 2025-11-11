package studio.one.base.security.authentication.lock.persistence;

import java.time.Instant;

public interface AccountLockRepository {

    int bumpFailedAttempts(String username, Instant now);

    int lockUntil(String username, Instant until);

    int resetLockState(String username);

    Integer findFailedAttempts(String username);

    Instant findLastFailedAt(String username);

    Instant findAccountLockedUntil(String username);
}
