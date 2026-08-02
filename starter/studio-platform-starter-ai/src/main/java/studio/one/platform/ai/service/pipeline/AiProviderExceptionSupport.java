package studio.one.platform.ai.service.pipeline;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
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

    public static boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof InterruptedIOException
                    || containsTimeoutSignal(current.getMessage())) {
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

    private static boolean containsTimeoutSignal(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("timed out")
                || lower.contains("timeout")
                || lower.contains("deadline exceeded");
    }
}
