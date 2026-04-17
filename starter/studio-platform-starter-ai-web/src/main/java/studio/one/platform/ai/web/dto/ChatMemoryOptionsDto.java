package studio.one.platform.ai.web.dto;

/**
 * Optional per-request chat memory settings.
 */
public record ChatMemoryOptionsDto(
        Boolean enabled,
        String conversationId
) {
}
