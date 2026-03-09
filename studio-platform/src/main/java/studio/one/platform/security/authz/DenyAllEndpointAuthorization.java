package studio.one.platform.security.authz;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * Fail-closed fallback authorization bean for SpEL when endpointAuthz is missing.
 */
@Slf4j
public class DenyAllEndpointAuthorization {

    private final AtomicBoolean warned = new AtomicBoolean(false);

    public boolean can(String resource, String action) {
        warnOnce();
        return false;
    }

    public boolean can(String domain, String component, String action) {
        warnOnce();
        return false;
    }

    public boolean any(String resource, String... actions) {
        warnOnce();
        return false;
    }

    public boolean any(String domain, String component, String... actions) {
        warnOnce();
        return false;
    }

    private void warnOnce() {
        if (warned.compareAndSet(false, true)) {
            log.error("endpointAuthz bean is missing; denying all endpoint access.");
        }
    }
}
