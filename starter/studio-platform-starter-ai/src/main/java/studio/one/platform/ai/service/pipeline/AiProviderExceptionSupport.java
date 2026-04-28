package studio.one.platform.ai.service.pipeline;

import java.util.Locale;

public final class AiProviderExceptionSupport {

    private AiProviderExceptionSupport() {
    }

    public static boolean isQuotaOrRateLimit(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (containsQuotaSignal(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsQuotaSignal(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("insufficient_quota")
                || lower.contains("quota")
                || lower.contains("rate limit")
                || lower.contains("resource_exhausted")
                || lower.contains("too_many_requests")
                || lower.contains("too many requests")
                || lower.contains("http 429")
                || lower.contains("status 429");
    }
}
