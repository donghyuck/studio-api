package studio.one.platform.security.authz.acl;

import java.time.Duration;

/**
 * Records ACL-related metrics such as counters and latency.
 */
public interface AclMetricsRecorder {

    /**
     * Records an ACL action with duration and optional count.
     *
     * @param action   action name (e.g., grant, revoke, bulk_revoke, delete, policy_sync)
     * @param duration elapsed time for the action
     * @param count    affected item count when applicable
     */
    default void record(String action, Duration duration, int count) {
        // no-op by default
    }

    /**
     * Returns a no-op recorder.
     */
    static AclMetricsRecorder noop() {
        return new AclMetricsRecorder() {};
    }
}
