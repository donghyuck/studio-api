package studio.one.base.user.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Component;

import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.service.UserMutator;

@Component
public class ApplicationUserMutator implements UserMutator<ApplicationUser> {

    @Override
    public void prepareForCreate(ApplicationUser user) {
        user.setUserId(null);
    }

    @Override
    public String getPassword(ApplicationUser user) {
        return user.getPassword();
    }

    @Override
    public void setPassword(ApplicationUser user, String encoded) {
        user.setPassword(encoded);
    }

    @Override
    public boolean isEnabled(ApplicationUser user) {
        return Boolean.TRUE.equals(user.isEnabled());
    }

    @Override
    public void setEnabled(ApplicationUser user, boolean enabled) {
        user.setEnabled(enabled);
    }

    @Override
    public int getFailedAttempts(ApplicationUser user) {
        return user.getFailedAttempts();
    }

    @Override
    public void setFailedAttempts(ApplicationUser user, int attempts) {
        user.setFailedAttempts(attempts);
    }

    @Override
    public Instant getLastFailedAt(ApplicationUser user) {
        return user.getLastFailedAt();
    }

    @Override
    public void setLastFailedAt(ApplicationUser user, Instant at) {
        user.setLastFailedAt(at);
    }

    @Override
    public Instant getAccountLockedUntil(ApplicationUser user) {
        return user.getAccountLockedUntil();
    }

    @Override
    public void setAccountLockedUntil(ApplicationUser user, Instant until) {
        user.setAccountLockedUntil(until);
    }
}
