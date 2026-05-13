package studio.one.platform.ai.autoconfigure.adapter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.chat.ChatResponseMetadata;
import studio.one.platform.ai.core.chat.ChatStreamEvent;
import studio.one.platform.ai.core.chat.ChatStreamEventType;
import studio.one.platform.ai.core.chat.TokenUsage;

/**
 * LangChain4j based {@link ChatPort} adapter.
 */
public class LangChainChatAdapter implements ChatPort {

    private final ChatModelFactory chatModelFactory;
    private final StreamingChatModelFactory streamingChatModelFactory;
    private final String provider;
    private final String configuredModel;
    private final boolean normalizeLeadingSystemMessages;

    public LangChainChatAdapter(ChatLanguageModel chatModel, String provider, String configuredModel) {
        this(request -> chatModel, null, provider, configuredModel);
    }

    public LangChainChatAdapter(ChatLanguageModel chatModel, String provider, String configuredModel, boolean supportsTopK) {
        this(chatModel, provider, configuredModel);
    }

    public static LangChainChatAdapter openAi(ChatLanguageModel chatModel, String provider, String configuredModel) {
        return new LangChainChatAdapter(chatModel, provider, configuredModel);
    }

    public LangChainChatAdapter(
            ChatModelFactory chatModelFactory,
            StreamingChatModelFactory streamingChatModelFactory,
            String provider,
            String configuredModel) {
        this(chatModelFactory, streamingChatModelFactory, provider, configuredModel, false);
    }

    public LangChainChatAdapter(
            ChatModelFactory chatModelFactory,
            StreamingChatModelFactory streamingChatModelFactory,
            String provider,
            String configuredModel,
            boolean normalizeLeadingSystemMessages) {
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory, "chatModelFactory");
        this.streamingChatModelFactory = streamingChatModelFactory;
        this.provider = normalize(provider);
        this.configuredModel = normalize(configuredModel);
        this.normalizeLeadingSystemMessages = normalizeLeadingSystemMessages;
    }

    public interface ChatModelFactory {
        ChatLanguageModel create(ChatRequest request);
    }

    public interface StreamingChatModelFactory {
        StreamingChatLanguageModel create(ChatRequest request);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        List<dev.langchain4j.data.message.ChatMessage> messages = toLangChainMessages(request);
        String requestedModel = firstNonBlank(request.model(), configuredModel);

        long startedAt = System.nanoTime();
        Response<AiMessage> providerResponse = chatModelFactory.create(request).generate(messages);
        long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        AiMessage aiMessage = providerResponse == null ? null : providerResponse.content();
        if (aiMessage == null || aiMessage.text() == null || aiMessage.text().isBlank()) {
            throw new IllegalStateException("Chat model returned an empty response");
        }

        String resolvedModel = requestedModel;
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (providerResponse != null && providerResponse.finishReason() != null) {
            metadata.put("finishReason", providerResponse.finishReason());
        }
        if (providerResponse != null && providerResponse.tokenUsage() != null) {
            metadata.put(ChatResponseMetadata.KEY_TOKEN_USAGE, tokenUsage(providerResponse.tokenUsage()));
        }
        metadata.put(ChatResponseMetadata.KEY_PROVIDER, provider);
        metadata.put(ChatResponseMetadata.KEY_RESOLVED_MODEL, resolvedModel);
        metadata.put(ChatResponseMetadata.KEY_LATENCY_MS, latencyMs);

        return new ChatResponse(List.of(ChatMessage.assistant(aiMessage.text())), resolvedModel, metadata);
    }

    private List<dev.langchain4j.data.message.ChatMessage> toLangChainMessages(ChatRequest request) {
        List<ChatMessage> sourceMessages = normalizeLeadingSystemMessages
                ? normalizeLeadingSystemMessages(request.messages())
                : request.messages();
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        for (ChatMessage message : sourceMessages) {
            messages.add(toLangChainMessage(message));
        }
        return messages;
    }

    @Override
    public Stream<ChatStreamEvent> stream(ChatRequest request) {
        if (streamingChatModelFactory == null) {
            return Stream.of(ChatStreamEvent.error(
                    provider + " chat streaming is not supported by the configured LangChain4j provider artifact",
                    ChatResponseMetadata.empty()));
        }

        List<dev.langchain4j.data.message.ChatMessage> messages = toLangChainMessages(request);
        String requestedModel = firstNonBlank(request.model(), configuredModel);
        BlockingQueue<ChatStreamEvent> events = new LinkedBlockingQueue<>();
        long startedAt = System.nanoTime();

        streamingChatModelFactory.create(request).generate(messages, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                events.add(ChatStreamEvent.delta(token, requestedModel, ChatResponseMetadata.empty()));
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (response != null && response.finishReason() != null) {
                    metadata.put("finishReason", response.finishReason());
                }
                if (response != null && response.tokenUsage() != null) {
                    metadata.put(ChatResponseMetadata.KEY_TOKEN_USAGE, tokenUsage(response.tokenUsage()));
                }
                metadata.put(ChatResponseMetadata.KEY_PROVIDER, provider);
                metadata.put(ChatResponseMetadata.KEY_RESOLVED_MODEL, requestedModel);
                metadata.put(ChatResponseMetadata.KEY_LATENCY_MS,
                        Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L));
                ChatResponseMetadata typedMetadata = ChatResponseMetadata.from(metadata);
                events.add(ChatStreamEvent.usage(typedMetadata));
                events.add(ChatStreamEvent.complete(requestedModel, typedMetadata));
            }

            @Override
            public void onError(Throwable error) {
                events.add(ChatStreamEvent.error(errorMessage(error), ChatResponseMetadata.empty()));
            }
        });

        Iterator<ChatStreamEvent> iterator = new Iterator<ChatStreamEvent>() {
            private ChatStreamEvent next;
            private boolean completed;

            @Override
            public boolean hasNext() {
                if (completed) {
                    return false;
                }
                if (next == null) {
                    next = take(events);
                }
                return true;
            }

            @Override
            public ChatStreamEvent next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                ChatStreamEvent current = next;
                next = null;
                completed = current.type() == ChatStreamEventType.COMPLETE
                        || current.type() == ChatStreamEventType.ERROR;
                return current;
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
    }

    private List<ChatMessage> normalizeLeadingSystemMessages(List<ChatMessage> messages) {
        List<String> leadingSystemMessages = new ArrayList<>();
        List<ChatMessage> normalized = new ArrayList<>();
        boolean nonSystemSeen = false;
        for (ChatMessage message : messages) {
            if (message.role() == studio.one.platform.ai.core.chat.ChatMessageRole.SYSTEM) {
                if (nonSystemSeen) {
                    throw new IllegalArgumentException(
                            provider + " chat provider only supports system messages before user or assistant messages");
                }
                leadingSystemMessages.add(message.content());
            } else {
                if (!nonSystemSeen && !leadingSystemMessages.isEmpty()) {
                    normalized.add(ChatMessage.system(String.join("\n\n", leadingSystemMessages)));
                    leadingSystemMessages.clear();
                }
                nonSystemSeen = true;
                normalized.add(message);
            }
        }
        if (!leadingSystemMessages.isEmpty()) {
            normalized.add(ChatMessage.system(String.join("\n\n", leadingSystemMessages)));
        }
        return normalized;
    }

    private dev.langchain4j.data.message.ChatMessage toLangChainMessage(ChatMessage message) {
        switch (message.role()) {
            case SYSTEM:
                return new SystemMessage(message.content());
            case USER:
                return new UserMessage(message.content());
            case ASSISTANT:
                return AiMessage.from(message.content());
            default:
                throw new IllegalArgumentException("Unsupported chat role: " + message.role());
        }
    }

    private Map<String, Integer> tokenUsage(dev.langchain4j.model.output.TokenUsage usage) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(TokenUsage.KEY_INPUT_TOKENS, usage.inputTokenCount());
        values.put(TokenUsage.KEY_OUTPUT_TOKENS, usage.outputTokenCount());
        values.put(TokenUsage.KEY_TOTAL_TOKENS, usage.totalTokenCount());
        return values;
    }

    private static ChatStreamEvent take(BlockingQueue<ChatStreamEvent> events) {
        try {
            return events.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChatStreamEvent.error("Interrupted while waiting for chat stream event", ChatResponseMetadata.empty());
        }
    }

    private static String errorMessage(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
