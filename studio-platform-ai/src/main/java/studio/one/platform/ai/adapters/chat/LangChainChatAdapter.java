package studio.one.platform.ai.adapters.chat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;

/**
 * Adapter that bridges LangChain4j {@link ChatModel} to the domain {@link ChatPort}.
 */
public class LangChainChatAdapter implements ChatPort {

    private final ChatModel chatModel;

    public LangChainChatAdapter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        List<dev.langchain4j.data.message.ChatMessage> langChainMessages = request.messages().stream()
                .map(this::toLangChainMessage)
                .toList();

        var parametersBuilder = ChatRequestParameters.builder();
        if (request.model() != null) {
            parametersBuilder.modelName(request.model());
        }
        if (request.temperature() != null) {
            parametersBuilder.temperature(request.temperature());
        }
        if (request.topP() != null) {
            parametersBuilder.topP(request.topP());
        }
        if (request.topK() != null) {
            parametersBuilder.topK(request.topK());
        }
        if (request.maxOutputTokens() != null) {
            parametersBuilder.maxOutputTokens(request.maxOutputTokens());
        }
        if (!request.stopSequences().isEmpty()) {
            parametersBuilder.stopSequences(request.stopSequences());
        }

        dev.langchain4j.model.chat.request.ChatRequest langChainRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(langChainMessages)
                        .parameters(parametersBuilder.build())
                        .build();

        dev.langchain4j.model.chat.response.ChatResponse providerResponse = chatModel.chat(langChainRequest);
        AiMessage aiMessage = providerResponse.aiMessage();
        if (aiMessage == null || aiMessage.text() == null) {
            throw new IllegalStateException("Chat model returned an empty response");
        }

        List<ChatMessage> responseMessages = List.of(ChatMessage.assistant(aiMessage.text()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("responseId", providerResponse.metadata().id());
        metadata.put("modelName", providerResponse.metadata().modelName());
        if (providerResponse.metadata().finishReason() != null) {
            metadata.put("finishReason", providerResponse.metadata().finishReason());
        }
        if (providerResponse.metadata().tokenUsage() != null) {
            metadata.put("tokenUsage", providerResponse.metadata().tokenUsage());
        }

        return new ChatResponse(responseMessages, providerResponse.metadata().modelName(), metadata);
    }

    private dev.langchain4j.data.message.ChatMessage toLangChainMessage(ChatMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> AiMessage.from(message.content());
        };
    }
}
