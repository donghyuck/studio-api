package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class ConversationSummaryDto {

    private final String conversationId;

    private final String title;

    private final String summary;

    private final int messageCount;

    private final Instant lastUpdatedAt;

    private final String status;

    @JsonCreator
    public ConversationSummaryDto(
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("title") String title,
            @JsonProperty("summary") String summary,
            @JsonProperty("messageCount") int messageCount,
            @JsonProperty("lastUpdatedAt") Instant lastUpdatedAt,
            @JsonProperty("status") String status
    ) {
        this.conversationId = conversationId;
        this.title = title;
        this.summary = summary;
        this.messageCount = messageCount;
        this.lastUpdatedAt = lastUpdatedAt;
        this.status = status;
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

    public int messageCount() {
        return messageCount;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public String status() {
        return status;
    }

    public String getStatus() {
        return status;
    }

}