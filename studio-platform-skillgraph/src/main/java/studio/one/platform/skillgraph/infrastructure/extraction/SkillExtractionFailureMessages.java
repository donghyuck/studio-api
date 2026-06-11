package studio.one.platform.skillgraph.infrastructure.extraction;

public final class SkillExtractionFailureMessages {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final String DEFAULT_MESSAGE = "Skill extraction failed";

    private SkillExtractionFailureMessages() {
    }

    public static String from(RuntimeException exception) {
        if (!(exception instanceof IllegalArgumentException)
                && !(exception instanceof SkillCandidateExtractionException)) {
            return DEFAULT_MESSAGE;
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return DEFAULT_MESSAGE;
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_MESSAGE_LENGTH);
    }
}
