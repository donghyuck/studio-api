package studio.one.platform.ai.autoconfigure.adapter;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import studio.one.platform.ai.core.chat.ChatRequest;

public class GoogleSpringAiChatAdapter extends SpringAiChatAdapter {

    public GoogleSpringAiChatAdapter(ChatModel chatModel) {
        super(chatModel);
    }

    public GoogleSpringAiChatAdapter(ChatModel chatModel, String provider, String configuredModel) {
        super(chatModel, provider, configuredModel);
    }

    @Override
    protected ChatOptions createChatOptions(ChatRequest request) {
        GoogleGenAiChatOptions.Builder builder = GoogleGenAiChatOptions.builder();
        if (request.model() != null) {
            builder.model(request.model());
        }
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        if (request.topP() != null) {
            builder.topP(request.topP());
        }
        if (request.topK() != null) {
            builder.topK(request.topK());
        }
        if (request.maxOutputTokens() != null) {
            builder.maxOutputTokens(request.maxOutputTokens());
        }
        if (!request.stopSequences().isEmpty()) {
            builder.stopSequences(request.stopSequences());
        }
        return builder.build();
    }
}
