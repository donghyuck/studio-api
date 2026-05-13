package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * DTO representing chat responses returned to API clients.
 */
public class ChatResponseDto {

    private final List<ChatMessageDto> messages;

    private final String model;

    private final Map<String, Object> metadata;

    @JsonCreator
    public ChatResponseDto(
            @JsonProperty("messages") List<ChatMessageDto> messages,
            @JsonProperty("model") String model,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        this.messages = messages;
        this.model = model;
        this.metadata = metadata;
    }

    public List<ChatMessageDto> messages() {
        return messages;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public String model() {
        return model;
    }

    public String getModel() {
        return model;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

}