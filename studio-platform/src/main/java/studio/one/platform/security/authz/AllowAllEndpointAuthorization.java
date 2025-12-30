package studio.one.platform.security.authz;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * Fallback authorization bean for SpEL when endpointAuthz is missing.
 * Always allows access but emits a warning once.
 */
@Slf4j
public class AllowAllEndpointAuthorization {

    private final AtomicBoolean warned = new AtomicBoolean(false);

    public boolean can(String resource, String action) {
        warnOnce();
        return true;
    }

    public boolean can(String domain, String component, String action) {
        warnOnce();
        return true;
    }

    public boolean any(String resource, String... actions) {
        warnOnce();
        return true;
    }

    public boolean any(String domain, String component, String... actions) {
        warnOnce();
        return true;
    }

    private void warnOnce() {
        if (warned.compareAndSet(false, true)) {
            log.warn("endpointAuthz bean is missing; allowing all endpoint access.");
        }
    }
}
