package studio.one.platform.security.acl.metrics;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import studio.one.platform.security.acl.AclMetricsRecorder;

/**
 * Micrometer-backed ACL metrics recorder.
 */
@RequiredArgsConstructor
public class MicrometerAclMetricsRecorder implements AclMetricsRecorder {

    private final MeterRegistry meterRegistry;

    @Override
    public void record(String action, Duration duration, int count) {
        String safeAction = (action == null || action.isBlank()) ? "unknown" : action;
        Duration safeDuration = (duration != null) ? duration : Duration.ZERO;

        Timer timer = Timer.builder("acl.operation.duration")
                .tag("action", safeAction)
                .register(meterRegistry);
        timer.record(safeDuration);

        Counter calls = Counter.builder("acl.operation.calls")
                .tag("action", safeAction)
                .register(meterRegistry);
        calls.increment();

        if (count > 0) {
            Counter affected = Counter.builder("acl.operation.affected")
                    .tag("action", safeAction)
                    .register(meterRegistry);
            affected.increment(count);
        }
    }
}
