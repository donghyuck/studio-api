package studio.one.platform.ai.core.chat;

import java.util.stream.Stream;

/**
 * Contract for interacting with a chat-capable AI provider.
 */
public interface ChatPort {

    ChatResponse chat(ChatRequest request);

    default Stream<ChatStreamEvent> stream(ChatRequest request) {
        try {
            ChatResponse response = chat(request);
            Stream<ChatStreamEvent> deltas = response.messages().stream()
                    .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                    .map(message -> ChatStreamEvent.delta(message.content(), response.model(), response.typedMetadata()));
            return Stream.concat(deltas, Stream.of(
                    ChatStreamEvent.usage(response.typedMetadata()),
                    ChatStreamEvent.complete(response.model(), response.typedMetadata())));
        } catch (RuntimeException e) {
            return Stream.of(ChatStreamEvent.error(e.getMessage(), ChatResponseMetadata.empty()));
        }
    }
}
