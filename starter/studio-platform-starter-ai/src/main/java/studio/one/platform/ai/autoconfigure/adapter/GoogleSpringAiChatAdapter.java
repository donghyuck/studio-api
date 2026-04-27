package studio.one.platform.ai.autoconfigure.adapter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatRequest;

public class GoogleSpringAiChatAdapter extends SpringAiChatAdapter {

    public GoogleSpringAiChatAdapter(ChatModel chatModel) {
        super(chatModel);
    }

    public GoogleSpringAiChatAdapter(ChatModel chatModel, String provider, String configuredModel) {
        super(chatModel, provider, configuredModel);
    }

    @Override
    protected List<ChatMessage> prepareMessages(ChatRequest request) {
        List<ChatMessage> messages = super.prepareMessages(request);
        if (messages.isEmpty()) {
            return messages;
        }
        List<String> leadingSystemContents = new ArrayList<>();
        int index = 0;
        while (index < messages.size() && messages.get(index).role() == ChatMessageRole.SYSTEM) {
            String content = messages.get(index).content();
            if (content != null && !content.isBlank()) {
                leadingSystemContents.add(content);
            }
            index++;
        }
        for (int i = index; i < messages.size(); i++) {
            if (messages.get(i).role() == ChatMessageRole.SYSTEM) {
                throw new IllegalArgumentException("Google GenAI supports only leading system messages");
            }
        }
        if (leadingSystemContents.size() <= 1) {
            return messages;
        }
        List<ChatMessage> prepared = new ArrayList<>(messages.size() - leadingSystemContents.size() + 1);
        prepared.add(ChatMessage.system(String.join("\n\n", leadingSystemContents)));
        prepared.addAll(messages.subList(index, messages.size()));
        return List.copyOf(prepared);
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
