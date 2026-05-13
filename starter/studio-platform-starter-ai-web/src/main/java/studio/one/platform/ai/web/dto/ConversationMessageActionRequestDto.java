package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

public class ConversationMessageActionRequestDto {

    @NotBlank(message = "conversationId is required")
    private final String conversationId;

    @NotBlank(message = "messageId is required")
    private final String messageId;

    private final String newConversationId;

    @JsonCreator
    public ConversationMessageActionRequestDto(
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("messageId") String messageId,
            @JsonProperty("newConversationId") String newConversationId
    ) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.newConversationId = newConversationId;
    }

    public String conversationId() {
        return conversationId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String messageId() {
        return messageId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String newConversationId() {
        return newConversationId;
    }

    public String getNewConversationId() {
        return newConversationId;
    }

}