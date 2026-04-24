package studio.one.platform.ai.core.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChatResponseMetadataTest {

    @Test
    void typedMetadataKeepsMapCompatibility() {
        ChatResponse response = new ChatResponse(
                List.of(ChatMessage.assistant("hello")),
                "gpt-4.1-mini",
                Map.of(
                        ChatResponseMetadata.KEY_TOKEN_USAGE, Map.of(
                                TokenUsage.KEY_INPUT_TOKENS, 10,
                                TokenUsage.KEY_OUTPUT_TOKENS, 5,
                                TokenUsage.KEY_TOTAL_TOKENS, 15),
                        ChatResponseMetadata.KEY_LATENCY_MS, 123L,
                        ChatResponseMetadata.KEY_PROVIDER, "openai",
                        ChatResponseMetadata.KEY_RESOLVED_MODEL, "gpt-4.1-mini",
                        ChatResponseMetadata.KEY_MEMORY_USED, true,
                        ChatResponseMetadata.KEY_CONVERSATION_ID, "conv-1"));

        assertThat(response.metadata()).containsEntry(ChatResponseMetadata.KEY_PROVIDER, "openai");
        assertThat(response.typedMetadata().tokenUsage().totalTokens()).isEqualTo(15);
        assertThat(response.typedMetadata().latencyMs()).isEqualTo(123L);
        assertThat(response.typedMetadata().memoryUsed()).isTrue();
        assertThat(response.typedMetadata().conversationId()).isEqualTo("conv-1");
    }

    @Test
    void typedMetadataFallsBackToLegacyModelName() {
        ChatResponseMetadata metadata = ChatResponseMetadata.from(Map.of("modelName", "legacy-model"));

        assertThat(metadata.resolvedModel()).isEqualTo("legacy-model");
        assertThat(metadata.toMap()).containsEntry(ChatResponseMetadata.KEY_RESOLVED_MODEL, "legacy-model");
    }

    @Test
    void chatPortDefaultStreamFallsBackToChatResponseEvents() {
        ChatPort port = request -> new ChatResponse(
                List.of(ChatMessage.assistant("delta")),
                "model",
                Map.of(ChatResponseMetadata.KEY_PROVIDER, "test"));
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hi")))
                .build();

        List<ChatStreamEvent> events = port.stream(request).toList();

        assertThat(events).extracting(ChatStreamEvent::type)
                .containsExactly(ChatStreamEventType.DELTA, ChatStreamEventType.USAGE, ChatStreamEventType.COMPLETE);
        assertThat(events.get(0).delta()).isEqualTo("delta");
        assertThat(events.get(0).metadata().provider()).isEqualTo("test");
    }
}
