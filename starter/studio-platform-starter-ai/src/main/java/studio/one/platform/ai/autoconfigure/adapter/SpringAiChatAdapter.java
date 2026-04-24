package studio.one.platform.ai.autoconfigure.adapter;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
import studio.one.platform.ai.core.chat.ChatResponseMetadata;
import studio.one.platform.ai.core.chat.ChatStreamEvent;
import studio.one.platform.ai.core.chat.TokenUsage;

/**
 * Spring AI based {@link ChatPort} adapter used for migration spike validation.
 */
public class SpringAiChatAdapter implements ChatPort {

    private final ChatModel chatModel;

    private final String provider;

    private final String configuredModel;

    public SpringAiChatAdapter(ChatModel chatModel) {
        this(chatModel, "", "");
    }

    public SpringAiChatAdapter(ChatModel chatModel, String provider, String configuredModel) {
        this.chatModel = chatModel;
        this.provider = normalize(provider);
        this.configuredModel = normalize(configuredModel);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        List<Message> messages = request.messages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        org.springframework.ai.chat.prompt.ChatOptions options = createChatOptions(request);
        long startedAt = System.nanoTime();
        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(
                options == null ? new Prompt(messages) : new Prompt(messages, options));
        long latencyMs = elapsedMillis(startedAt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Chat model returned an empty response");
        }
        AssistantMessage assistant = response.getResult().getOutput();
        if (assistant.getText() == null) {
            throw new IllegalStateException("Chat model returned an empty response");
        }

        Map<String, Object> metadata = metadata(response, latencyMs, request.model());
        if (response.getResult().getMetadata() != null && response.getResult().getMetadata().getFinishReason() != null) {
            metadata.put("finishReason", response.getResult().getMetadata().getFinishReason());
        }
        metadata.put("generationMetadata", response.getResult().getMetadata());

        return new ChatResponse(
                List.of(ChatMessage.assistant(assistant.getText())),
                resolvedModel(response, request.model()),
                metadata);
    }

    @Override
    public Stream<ChatStreamEvent> stream(ChatRequest request) {
        List<Message> messages = request.messages().stream()
                .map(this::toSpringAiMessage)
                .toList();
        org.springframework.ai.chat.prompt.ChatOptions options = createChatOptions(request);
        Prompt prompt = options == null ? new Prompt(messages) : new Prompt(messages, options);
        long startedAt = System.nanoTime();
        try {
            // ChatPort exposes a synchronous Stream contract; callers must not consume it on an event-loop thread.
            Iterator<org.springframework.ai.chat.model.ChatResponse> source =
                    chatModel.stream(prompt).toIterable().iterator();
            Iterator<ChatStreamEvent> events = new SpringAiStreamIterator(source, request, startedAt);
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(events, 0), false);
        } catch (UnsupportedOperationException e) {
            return ChatPort.super.stream(request);
        }
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

    private ChatStreamEvent streamEvent(
            org.springframework.ai.chat.model.ChatResponse response,
            String requestedModel,
            long startedAt) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return ChatStreamEvent.delta("", requestedModel, ChatResponseMetadata.empty());
        }
        AssistantMessage assistant = response.getResult().getOutput();
        String text = assistant.getText() == null ? "" : assistant.getText();
        long latencyMs = elapsedMillis(startedAt);
        return ChatStreamEvent.delta(text, resolvedModel(response, requestedModel),
                ChatResponseMetadata.from(metadata(response, latencyMs, requestedModel)));
    }

    private Map<String, Object> metadata(
            org.springframework.ai.chat.model.ChatResponse response,
            long latencyMs,
            String requestedModel) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        org.springframework.ai.chat.metadata.ChatResponseMetadata responseMetadata = response.getMetadata();
        if (responseMetadata != null) {
            metadata.put("responseId", responseMetadata.getId());
            metadata.put("modelName", responseMetadata.getModel());
        }
        metadata.put(ChatResponseMetadata.KEY_PROVIDER, provider);
        metadata.put(ChatResponseMetadata.KEY_RESOLVED_MODEL, resolvedModel(response, requestedModel));
        metadata.put(ChatResponseMetadata.KEY_LATENCY_MS, latencyMs);
        if (responseMetadata != null && responseMetadata.getUsage() != null) {
            metadata.put(ChatResponseMetadata.KEY_TOKEN_USAGE, toTokenUsageMap(responseMetadata.getUsage()));
        }
        if (responseMetadata != null) {
            metadata.put("chatResponseMetadata", responseMetadata);
        }
        return metadata;
    }

    private String resolvedModel(org.springframework.ai.chat.model.ChatResponse response, String requestedModel) {
        org.springframework.ai.chat.metadata.ChatResponseMetadata responseMetadata = response.getMetadata();
        return firstNonBlank(responseMetadata == null ? null : responseMetadata.getModel(), requestedModel, configuredModel);
    }

    private Map<String, Integer> toTokenUsageMap(Usage usage) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(TokenUsage.KEY_INPUT_TOKENS, usage.getPromptTokens());
        values.put(TokenUsage.KEY_OUTPUT_TOKENS, usage.getCompletionTokens());
        values.put(TokenUsage.KEY_TOTAL_TOKENS, usage.getTotalTokens());
        return values;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String normalize(String value) {
        return Objects.toString(value, "").trim();
    }

    private Stream<ChatStreamEvent> fallbackStream(ChatRequest request) {
        return ChatPort.super.stream(request);
    }

    private final class SpringAiStreamIterator implements Iterator<ChatStreamEvent> {

        private final Iterator<org.springframework.ai.chat.model.ChatResponse> source;

        private final ChatRequest request;

        private final long startedAt;

        private Iterator<ChatStreamEvent> fallback;

        private ChatStreamEvent last;

        private boolean sourceExhausted;

        private int terminalIndex;

        private SpringAiStreamIterator(
                Iterator<org.springframework.ai.chat.model.ChatResponse> source,
                ChatRequest request,
                long startedAt) {
            this.source = source;
            this.request = request;
            this.startedAt = startedAt;
        }

        @Override
        public boolean hasNext() {
            if (fallback != null) {
                return fallback.hasNext();
            }
            if (!sourceExhausted && source.hasNext()) {
                return true;
            }
            sourceExhausted = true;
            if (last == null) {
                fallback = fallbackStream(request).iterator();
                return fallback.hasNext();
            }
            return terminalIndex < 2;
        }

        @Override
        public ChatStreamEvent next() {
            if (fallback != null) {
                return fallback.next();
            }
            if (!sourceExhausted && source.hasNext()) {
                last = streamEvent(source.next(), request.model(), startedAt);
                return last;
            }
            sourceExhausted = true;
            if (last == null) {
                fallback = fallbackStream(request).iterator();
                return fallback.next();
            }
            if (terminalIndex == 0) {
                terminalIndex++;
                return ChatStreamEvent.usage(last.metadata());
            }
            if (terminalIndex == 1) {
                terminalIndex++;
                String model = last.model().isBlank() ? request.model() : last.model();
                return ChatStreamEvent.complete(model, last.metadata());
            }
            throw new NoSuchElementException();
        }
    }
}
