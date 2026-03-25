package studio.one.platform.ai.autoconfigure.adapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;

/**
 * Spring AI based {@link ChatPort} adapter used for migration spike validation.
 */
public class SpringAiChatAdapter implements ChatPort {

    private final ChatModel chatModel;

    public SpringAiChatAdapter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        List<Message> messages = request.messages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        org.springframework.ai.chat.prompt.ChatOptions options = createChatOptions(request);
        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(
                options == null ? new Prompt(messages) : new Prompt(messages, options));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Chat model returned an empty response");
        }
        AssistantMessage assistant = response.getResult().getOutput();
        if (assistant.getText() == null) {
            throw new IllegalStateException("Chat model returned an empty response");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("responseId", response.getMetadata().getId());
        metadata.put("modelName", response.getMetadata().getModel());
        if (response.getResult().getMetadata() != null && response.getResult().getMetadata().getFinishReason() != null) {
            metadata.put("finishReason", response.getResult().getMetadata().getFinishReason());
        }
        if (response.getMetadata().getUsage() != null) {
            metadata.put("tokenUsage", toTokenUsageMap(response.getMetadata().getUsage()));
        }
        metadata.put("chatResponseMetadata", response.getMetadata());
        metadata.put("generationMetadata", response.getResult().getMetadata());

        return new ChatResponse(
                List.of(ChatMessage.assistant(assistant.getText())),
                response.getMetadata().getModel() != null ? response.getMetadata().getModel() : request.model(),
                metadata);
    }

    private Message toSpringAiMessage(ChatMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    protected org.springframework.ai.chat.prompt.ChatOptions createChatOptions(ChatRequest request) {
        return null;
    }

    private Map<String, Integer> toTokenUsageMap(Usage usage) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("inputTokens", usage.getPromptTokens());
        values.put("outputTokens", usage.getCompletionTokens());
        values.put("totalTokens", usage.getTotalTokens());
        return values;
    }
}
