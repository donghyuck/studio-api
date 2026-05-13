package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Optional per-request chat memory settings.
 */
public class ChatMemoryOptionsDto {

    private final Boolean enabled;

    private final String conversationId;

    @JsonCreator
    public ChatMemoryOptionsDto(
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("conversationId") String conversationId
    ) {
        this.enabled = enabled;
        this.conversationId = conversationId;
    }

    public Boolean enabled() {
        return enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String conversationId() {
        return conversationId;
    }

    public String getConversationId() {
        return conversationId;
    }

}