package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.Optional;

public final class PdfExtractionOptionsContext {
    private static final ThreadLocal<Boolean> OCR_REQUIRED = new ThreadLocal<>();
    private static final ThreadLocal<String> OCR_LANGUAGE = new ThreadLocal<>();
    private static final ThreadLocal<String> OCR_MODE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> MATH_VISION_CORRECTION = new ThreadLocal<>();

    private PdfExtractionOptionsContext() {
    }

    public static Optional<Boolean> ocrRequired() {
        return Optional.ofNullable(OCR_REQUIRED.get());
    }

    public static Optional<String> ocrLanguage() {
        return Optional.ofNullable(OCR_LANGUAGE.get());
    }

    public static Optional<String> ocrMode() {
        return Optional.ofNullable(OCR_MODE.get());
    }

    public static Optional<Boolean> mathVisionCorrection() {
        return Optional.ofNullable(MATH_VISION_CORRECTION.get());
    }

    public static Scope withOcrRequired(Boolean ocrRequired) {
        return withOptions(ocrRequired, null);
    }

    public static Scope withOptions(Boolean ocrRequired, String ocrLanguage) {
        return withOptions(ocrRequired, ocrLanguage, null);
    }

    public static Scope withOptions(Boolean ocrRequired, String ocrLanguage, String ocrMode) {
        return withOptions(ocrRequired, ocrLanguage, ocrMode, null);
    }

    public static Scope withOptions(Boolean ocrRequired, String ocrLanguage, String ocrMode,
            Boolean mathVisionCorrection) {
        Boolean previous = OCR_REQUIRED.get();
        String previousLanguage = OCR_LANGUAGE.get();
        String previousMode = OCR_MODE.get();
        Boolean previousMathVisionCorrection = MATH_VISION_CORRECTION.get();
        if (ocrRequired == null) {
            OCR_REQUIRED.remove();
        } else {
            OCR_REQUIRED.set(ocrRequired);
        }
        if (ocrLanguage == null || ocrLanguage.isBlank()) {
            OCR_LANGUAGE.remove();
        } else {
            OCR_LANGUAGE.set(ocrLanguage.trim());
        }
        if (ocrMode == null || ocrMode.isBlank()) {
            OCR_MODE.remove();
        } else {
            OCR_MODE.set(ocrMode.trim());
        }
        if (mathVisionCorrection == null) {
            MATH_VISION_CORRECTION.remove();
        } else {
            MATH_VISION_CORRECTION.set(mathVisionCorrection);
        }
        return new Scope(previous, previousLanguage, previousMode, previousMathVisionCorrection);
    }

    public static final class Scope implements AutoCloseable {
        private final Boolean previous;
        private final String previousLanguage;
        private final String previousMode;
        private final Boolean previousMathVisionCorrection;

        private Scope(Boolean previous, String previousLanguage, String previousMode,
                Boolean previousMathVisionCorrection) {
            this.previous = previous;
            this.previousLanguage = previousLanguage;
            this.previousMode = previousMode;
            this.previousMathVisionCorrection = previousMathVisionCorrection;
        }

        @Override
        public void close() {
            if (previous == null) {
                OCR_REQUIRED.remove();
            } else {
                OCR_REQUIRED.set(previous);
            }
            if (previousLanguage == null) {
                OCR_LANGUAGE.remove();
            } else {
                OCR_LANGUAGE.set(previousLanguage);
            }
            if (previousMode == null) {
                OCR_MODE.remove();
            } else {
                OCR_MODE.set(previousMode);
            }
            if (previousMathVisionCorrection == null) {
                MATH_VISION_CORRECTION.remove();
            } else {
                MATH_VISION_CORRECTION.set(previousMathVisionCorrection);
            }
        }
    }
}
