package studio.one.platform.ai.web.dto;

public record RagQuestionSuggestionCapabilitiesDto(
        boolean enabled,
        String contractVersion,
        int maxSuggestions) {
}
