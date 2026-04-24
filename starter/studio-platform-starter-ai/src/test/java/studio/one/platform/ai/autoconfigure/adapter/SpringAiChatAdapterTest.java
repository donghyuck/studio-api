package studio.one.platform.ai.autoconfigure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatStreamEvent;
import studio.one.platform.ai.core.chat.ChatStreamEventType;

class SpringAiChatAdapterTest {

    @Test
    void mapsAssistantResponseIntoChatPortContract() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(
                        List.of(new Generation(
                                new AssistantMessage("hello from spring ai"),
                                ChatGenerationMetadata.builder().finishReason("stop").build())),
                        ChatResponseMetadata.builder()
                                .id("resp-1")
                                .model("gpt-4.1-mini")
                                .usage(new DefaultUsage(5, 7))
                                .build()));

        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model, "OPENAI", "configured-model");

        studio.one.platform.ai.core.chat.ChatResponse response = adapter.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .model("gpt-4o-mini")
                .build());

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).content()).isEqualTo("hello from spring ai");
        assertThat(response.messages().get(0).role()).isEqualTo(studio.one.platform.ai.core.chat.ChatMessageRole.ASSISTANT);
        assertThat(response.model()).isEqualTo("gpt-4.1-mini");
        assertThat(response.metadata()).containsEntry("responseId", "resp-1");
        assertThat(response.metadata()).containsEntry("modelName", "gpt-4.1-mini");
        assertThat(response.metadata()).containsEntry(studio.one.platform.ai.core.chat.ChatResponseMetadata.KEY_PROVIDER, "OPENAI");
        assertThat(response.metadata()).containsEntry(studio.one.platform.ai.core.chat.ChatResponseMetadata.KEY_RESOLVED_MODEL, "gpt-4.1-mini");
        assertThat(response.metadata()).containsKey(studio.one.platform.ai.core.chat.ChatResponseMetadata.KEY_LATENCY_MS);
        assertThat(response.metadata()).containsEntry("finishReason", "stop");
        assertThat(response.metadata().get("tokenUsage"))
                .isEqualTo(Map.of("inputTokens", 5, "outputTokens", 7, "totalTokens", 12));
        assertThat(response.metadata()).containsKeys("chatResponseMetadata", "generationMetadata");
        assertThat(response.typedMetadata().provider()).isEqualTo("OPENAI");
        assertThat(response.typedMetadata().resolvedModel()).isEqualTo("gpt-4.1-mini");
    }

    @Test
    void failsWithExplicitErrorWhenProviderReturnsEmptyResponse() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(List.of()));

        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model);

        assertThatThrownBy(() -> adapter.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void handlesMissingSpringMetadataAndFallsBackToConfiguredModel() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(
                        List.of(new Generation(new AssistantMessage("hello"))),
                        null));
        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model, "OPENAI", "configured-model");

        studio.one.platform.ai.core.chat.ChatResponse response = adapter.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build());

        assertThat(response.model()).isEqualTo("configured-model");
        assertThat(response.metadata()).doesNotContainKeys("responseId", "modelName", "chatResponseMetadata");
        assertThat(response.typedMetadata().provider()).isEqualTo("OPENAI");
        assertThat(response.typedMetadata().resolvedModel()).isEqualTo("configured-model");
    }

    @Test
    void mapsNativeSpringAiStreamIntoChatStreamEvents() {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class)))
                .thenReturn(Flux.just(
                        new ChatResponse(
                                List.of(new Generation(new AssistantMessage("hel"))),
                                ChatResponseMetadata.builder()
                                        .id("stream-1")
                                        .model("gpt-stream")
                                        .usage(new DefaultUsage(2, 1))
                                        .build()),
                        new ChatResponse(
                                List.of(new Generation(new AssistantMessage("lo"))),
                                ChatResponseMetadata.builder()
                                        .id("stream-1")
                                        .model("gpt-stream")
                                        .usage(new DefaultUsage(2, 2))
                                        .build())));
        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model, "OPENAI", "configured-model");

        List<ChatStreamEvent> events = adapter.stream(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .model("requested-model")
                .build()).toList();

        assertThat(events).extracting(ChatStreamEvent::type)
                .containsExactly(
                        ChatStreamEventType.DELTA,
                        ChatStreamEventType.DELTA,
                        ChatStreamEventType.USAGE,
                        ChatStreamEventType.COMPLETE);
        assertThat(events).extracting(ChatStreamEvent::delta)
                .containsExactly("hel", "lo", "", "");
        assertThat(events.get(0).metadata().provider()).isEqualTo("OPENAI");
        assertThat(events.get(3).model()).isEqualTo("gpt-stream");
    }

    @Test
    void fallsBackToChatWhenNativeStreamIsUnsupported() {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenThrow(new UnsupportedOperationException("no stream"));
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(
                        List.of(new Generation(new AssistantMessage("fallback"))),
                        ChatResponseMetadata.builder()
                                .model("fallback-model")
                                .build()));
        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model, "OPENAI", "configured-model");

        List<ChatStreamEvent> events = adapter.stream(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build()).toList();

        assertThat(events).extracting(ChatStreamEvent::type)
                .containsExactly(ChatStreamEventType.DELTA, ChatStreamEventType.USAGE, ChatStreamEventType.COMPLETE);
        assertThat(events.get(0).delta()).isEqualTo("fallback");
    }

    @Test
    void fallsBackToChatWhenNativeStreamIsEmpty() {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.empty());
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(
                        List.of(new Generation(new AssistantMessage("fallback"))),
                        ChatResponseMetadata.builder()
                                .model("fallback-model")
                                .build()));
        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model, "OPENAI", "configured-model");

        List<ChatStreamEvent> events = adapter.stream(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build()).toList();

        assertThat(events).extracting(ChatStreamEvent::type)
                .containsExactly(ChatStreamEventType.DELTA, ChatStreamEventType.USAGE, ChatStreamEventType.COMPLETE);
        assertThat(events.get(0).delta()).isEqualTo("fallback");
    }

    @Test
    void propagatesNativeStreamRuntimeFailures() {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.error(new IllegalStateException("provider broken")));
        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model, "OPENAI", "configured-model");

        assertThatThrownBy(() -> adapter.stream(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build()).toList())
                .hasMessageContaining("provider broken");
    }
}
