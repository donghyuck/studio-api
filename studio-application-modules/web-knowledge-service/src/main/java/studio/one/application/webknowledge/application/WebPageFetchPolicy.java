package studio.one.application.webknowledge.application;

import java.time.Duration;

public record WebPageFetchPolicy(
        Duration connectTimeout,
        Duration requestTimeout,
        int maxResponseBytes,
        int maxNormalizedChars,
        int maxRedirects,
        boolean robotsEnabled,
        String userAgent) {

    public static WebPageFetchPolicy defaults() {
        return new WebPageFetchPolicy(
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                5 * 1024 * 1024,
                2_000_000,
                3,
                true,
                "StudioOne-WebKnowledge/1.0");
    }
}
