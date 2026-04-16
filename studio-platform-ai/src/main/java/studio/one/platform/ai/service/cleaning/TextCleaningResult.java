package studio.one.platform.ai.service.cleaning;

/**
 * Result of a text cleaning attempt.
 */
public record TextCleaningResult(
        String text,
        boolean cleaned,
        String cleanerPrompt) {

    public static TextCleaningResult skipped(String text) {
        return new TextCleaningResult(text, false, null);
    }
}
