package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.Map;

public final class PdfExtractionProgressContext {
    private static final ThreadLocal<Listener> CURRENT = new ThreadLocal<>();

    private PdfExtractionProgressContext() {
    }

    public static Scope withListener(Listener listener) {
        Listener previous = CURRENT.get();
        if (listener == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(listener);
        }
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    static void publishPart(Map<String, Object> partSummary) {
        Listener listener = CURRENT.get();
        if (listener != null) {
            listener.onPart(partSummary);
        }
    }

    @FunctionalInterface
    public interface Listener {
        void onPart(Map<String, Object> partSummary);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
