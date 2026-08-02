package studio.one.application.webknowledge.application;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Process-wide origin scheduler shared by crawl and preview operations.
 */
final class WebOriginRequestScheduler {

    private static final ConcurrentHashMap<String, AtomicLong> NEXT_REQUEST_NANOS =
            new ConcurrentHashMap<>();

    private WebOriginRequestScheduler() {
    }

    static void await(URI uri, Duration delay) {
        if (uri == null || uri.getHost() == null || delay == null
                || delay.isZero() || delay.isNegative()) {
            return;
        }
        long interval = Math.max(0L, delay.toNanos());
        AtomicLong next = NEXT_REQUEST_NANOS.computeIfAbsent(
                originKey(uri),
                ignored -> new AtomicLong());
        while (true) {
            long now = System.nanoTime();
            long current = next.get();
            long start = Math.max(now, current);
            if (next.compareAndSet(current, start + interval)) {
                if (start > now) {
                    LockSupport.parkNanos(start - now);
                }
                return;
            }
        }
    }

    private static String originKey(URI uri) {
        int port = uri.getPort() >= 0 ? uri.getPort() : 443;
        return uri.getScheme().toLowerCase(Locale.ROOT)
                + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
    }
}
