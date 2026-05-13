package studio.one.platform.ai.service.cleaning;

/**
 * Result of a text cleaning attempt.
 */
public final class TextCleaningResult {

    private final String text;
    private final boolean cleaned;
    private final String cleanerPrompt;

    public TextCleaningResult(
            String text,
            boolean cleaned,
            String cleanerPrompt
    ) {
        this.text = text;
        this.cleaned = cleaned;
        this.cleanerPrompt = cleanerPrompt;
    }

    public String text() {
        return text;
    }

    public boolean cleaned() {
        return cleaned;
    }

    public String cleanerPrompt() {
        return cleanerPrompt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TextCleaningResult)) {
            return false;
        }
        TextCleaningResult that = (TextCleaningResult) o;
        return java.util.Objects.equals(text, that.text)
                && cleaned == that.cleaned
                && java.util.Objects.equals(cleanerPrompt, that.cleanerPrompt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(text, cleaned, cleanerPrompt);
    }

    @Override
    public String toString() {
        return "TextCleaningResult[" +
                "text=" + text + ", " +
                "cleaned=" + cleaned + ", " +
                "cleanerPrompt=" + cleanerPrompt +
                "]";
    }

    public static TextCleaningResult skipped(String text) {
        return new TextCleaningResult(text, false, null);
    }
}
