package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

/**
 * DTO representing a chat message exchanged with an AI provider.
 */
public class ChatMessageDto {

    @NotBlank
    private final String role;

    @NotBlank
    private final String content;

    @JsonCreator
    public ChatMessageDto(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content
    ) {
        this.role = role;
        this.content = content;
    }

    public String role() {
        return role;
    }

    public String getRole() {
        return role;
    }

    public String content() {
        return content;
    }

    public String getContent() {
        return content;
    }

}