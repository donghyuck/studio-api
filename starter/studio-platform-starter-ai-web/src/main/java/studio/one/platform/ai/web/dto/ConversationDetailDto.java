package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ConversationDetailDto {

    private final String conversationId;

    private final String title;

    private final String summary;

    private final String status;

    private final String parentConversationId;

    private final String forkedFromMessageId;

    private final int messageCount;

    private final Instant createdAt;

    private final Instant lastUpdatedAt;

    private final Map<String, Object> metadata;

    private final List<ConversationMessageDto> messages;

    @JsonCreator
    public ConversationDetailDto(
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("title") String title,
            @JsonProperty("summary") String summary,
            @JsonProperty("status") String status,
            @JsonProperty("parentConversationId") String parentConversationId,
            @JsonProperty("forkedFromMessageId") String forkedFromMessageId,
            @JsonProperty("messageCount") int messageCount,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("lastUpdatedAt") Instant lastUpdatedAt,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("messages") List<ConversationMessageDto> messages
    ) {
        this.conversationId = conversationId;
        this.title = title;
        this.summary = summary;
        this.status = status;
        this.parentConversationId = parentConversationId;
        this.forkedFromMessageId = forkedFromMessageId;
        this.messageCount = messageCount;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
        this.metadata = metadata;
        this.messages = messages;
    }

    public String conversationId() {
        return conversationId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String title() {
        return title;
    }

    public String getTitle() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public String getSummary() {
        return summary;
    }

    public String status() {
        return status;
    }

    public String getStatus() {
        return status;
    }

    public String parentConversationId() {
        return parentConversationId;
    }

    public String getParentConversationId() {
        return parentConversationId;
    }

    public String forkedFromMessageId() {
        return forkedFromMessageId;
    }

    public String getForkedFromMessageId() {
        return forkedFromMessageId;
    }

    public int messageCount() {
        return messageCount;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<ConversationMessageDto> messages() {
        return messages;
    }

    public List<ConversationMessageDto> getMessages() {
        return messages;
    }

}