package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.Valid;

public class ConversationActionRequestDto {

    @NotBlank(message = "conversationId is required")
    private final String conversationId;

    private final String messageId;

    private final String newConversationId;

    private final String summary;

    @Valid
    private final ChatRequestDto chat;

    @JsonCreator
    public ConversationActionRequestDto(
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("messageId") String messageId,
            @JsonProperty("newConversationId") String newConversationId,
            @JsonProperty("summary") String summary,
            @JsonProperty("chat") ChatRequestDto chat
    ) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.newConversationId = newConversationId;
        this.summary = summary;
        this.chat = chat;
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

    public String summary() {
        return summary;
    }

    public String getSummary() {
        return summary;
    }

    public ChatRequestDto chat() {
        return chat;
    }

    public ChatRequestDto getChat() {
        return chat;
    }

}