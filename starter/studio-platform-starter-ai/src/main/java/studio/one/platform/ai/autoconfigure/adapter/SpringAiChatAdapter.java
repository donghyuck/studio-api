package studio.one.platform.ai.autoconfigure.adapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;

import studio.one.platform.ai.core.chat.ChatMessage;
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

        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(new Prompt(messages, toChatOptions(request)));
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
            metadata.put("tokenUsage", response.getMetadata().getUsage());
        }
        metadata.put("chatResponseMetadata", response.getMetadata());
        metadata.put("generationMetadata", response.getResult().getMetadata());

        return new ChatResponse(
                List.of(ChatMessage.assistant(assistant.getText())),
                response.getMetadata().getModel() != null ? response.getMetadata().getModel() : request.model(),
                metadata);
    }

    private ChatOptions toChatOptions(ChatRequest request) {
        ChatOptions.Builder builder = ChatOptions.builder();
        boolean configured = false;
        if (request.model() != null) {
            builder.model(request.model());
            configured = true;
        }
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
            configured = true;
        }
        if (request.topP() != null) {
            builder.topP(request.topP());
            configured = true;
        }
        if (request.topK() != null) {
            builder.topK(request.topK());
            configured = true;
        }
        if (request.maxOutputTokens() != null) {
            builder.maxTokens(request.maxOutputTokens());
            configured = true;
        }
        if (!request.stopSequences().isEmpty()) {
            builder.stopSequences(request.stopSequences());
            configured = true;
        }
        return configured ? builder.build() : null;
    }

    private Message toSpringAiMessage(ChatMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }
}
