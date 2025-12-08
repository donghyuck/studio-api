package studio.one.custom.user.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import studio.one.base.user.service.UserMutator;
import studio.one.custom.user.domain.entity.CustomUser;

/**
 * CustomUser에 맞춘 UserMutator 구현.
 */
@Component
public class CustomUserMutator implements UserMutator<CustomUser> {

    @Override
    public void prepareForCreate(CustomUser user) {
        user.setUserId(null);
    }

    @Override
    public String getPassword(CustomUser user) {
        return user.getPassword();
    }

    @Override
    public void setPassword(CustomUser user, String encoded) {
        user.setPassword(encoded);
    }

    @Override
    public boolean isEnabled(CustomUser user) {
        return user.isEnabled();
    }

    @Override
    public void setEnabled(CustomUser user, boolean enabled) {
        user.setEnabled(enabled);
    }

    @Override
    public int getFailedAttempts(CustomUser user) {
        return user.getFailedAttempts();
    }

    @Override
    public void setFailedAttempts(CustomUser user, int attempts) {
        user.setFailedAttempts(attempts);
    }

    @Override
    public Instant getLastFailedAt(CustomUser user) {
        return user.getLastFailedAt();
    }

    @Override
    public void setLastFailedAt(CustomUser user, Instant at) {
        user.setLastFailedAt(at);
    }

    @Override
    public Instant getAccountLockedUntil(CustomUser user) {
        return user.getAccountLockedUntil();
    }

    @Override
    public void setAccountLockedUntil(CustomUser user, Instant until) {
        user.setAccountLockedUntil(until);
    }
}
