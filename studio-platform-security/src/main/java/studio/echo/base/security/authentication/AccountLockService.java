package studio.echo.base.security.authentication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface AccountLockService {

    void onFailedLogin(String username);

    void onSuccessfulLogin(String username);

    Optional<Instant> getLockedUntil(String username);

    default long getRemainingLockSeconds(String username, Clock clock) {
        return getLockedUntil(username)
                .map(until -> Math.max(0, Duration.between(Instant.now(clock), until).getSeconds()))
                .orElse(0L);
    }
}
