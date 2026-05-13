package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public class ConversationMessageDto {

    private final String messageId;

    private final String role;

    private final String content;

    private final Instant createdAt;

    private final Map<String, Object> metadata;

    @JsonCreator
    public ConversationMessageDto(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("role") String role,
            @JsonProperty("content") String content,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        this.messageId = messageId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.metadata = metadata;
    }

    public String messageId() {
        return messageId;
    }

    public String getMessageId() {
        return messageId;
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

    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

}