package studio.one.platform.ai.core.chat;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates a request to a chat-capable AI provider.
 */
public final class ChatRequest {

    private final List<ChatMessage> messages;
    private final String model;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final Integer maxOutputTokens;
    private final List<String> stopSequences;

    private ChatRequest(Builder builder) {
        Objects.requireNonNull(builder.messages, "messages");
        if (builder.messages.isEmpty()) {
            throw new IllegalArgumentException("At least one chat message is required");
        }
        this.messages = List.copyOf(builder.messages);
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.stopSequences = builder.stopSequences == null
                ? Collections.emptyList()
                : List.copyOf(builder.stopSequences);
    }

    public List<ChatMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    public String model() {
        return model;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer topK() {
        return topK;
    }

    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    public List<String> stopSequences() {
        return Collections.unmodifiableList(stopSequences);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private List<ChatMessage> messages;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer maxOutputTokens;
        private List<String> stopSequences;

        private Builder() {
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
