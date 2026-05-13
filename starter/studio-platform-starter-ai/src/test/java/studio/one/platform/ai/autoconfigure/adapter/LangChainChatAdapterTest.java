package studio.one.platform.ai.autoconfigure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.chat.ChatStreamEvent;
import studio.one.platform.ai.core.chat.ChatStreamEventType;

class LangChainChatAdapterTest {

    @Test
    void shouldPassRequestOverridesToChatModelFactory() {
        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();
        LangChainChatAdapter adapter = new LangChainChatAdapter(
                request -> {
                    capturedRequest.set(request);
                    return messages -> Response.from(AiMessage.from("answer"));
                },
                null,
                "OPENAI",
                "configured-model");

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("question")))
                .model("request-model")
                .temperature(0.2d)
                .topP(0.8d)
                .topK(20)
                .maxOutputTokens(256)
                .stopSequences(List.of("stop"))
                .build();

        ChatResponse response = adapter.chat(request);

        assertThat(capturedRequest.get()).isSameAs(request);
        assertThat(response.model()).isEqualTo("request-model");
        assertThat(response.messages()).containsExactly(ChatMessage.assistant("answer"));
    }

    @Test
    void shouldUseStreamingModelFactoryForStreamRequests() {
        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();
        LangChainChatAdapter adapter = new LangChainChatAdapter(
                request -> {
                    throw new AssertionError("stream must not use blocking chat model");
                },
                request -> {
                    capturedRequest.set(request);
                    return (messages, handler) -> {
                        handler.onNext("hel");
                        handler.onNext("lo");
                        handler.onComplete(Response.from(AiMessage.from("hello")));
                    };
                },
                "OLLAMA",
                "configured-model");
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("question")))
                .model("request-model")
                .build();

        List<ChatStreamEvent> events = adapter.stream(request).collect(Collectors.toList());

        assertThat(capturedRequest.get()).isSameAs(request);
        assertThat(events).extracting(ChatStreamEvent::type)
                .containsExactly(ChatStreamEventType.DELTA, ChatStreamEventType.DELTA,
                        ChatStreamEventType.USAGE, ChatStreamEventType.COMPLETE);
        assertThat(events.get(0).delta()).isEqualTo("hel");
        assertThat(events.get(1).delta()).isEqualTo("lo");
        assertThat(events.get(3).model()).isEqualTo("request-model");
    }

    @Test
    void shouldReturnExplicitErrorWhenStreamingIsUnsupported() {
        LangChainChatAdapter adapter = new LangChainChatAdapter(
                messages -> Response.from(AiMessage.from("answer")),
                "GOOGLE_AI_GEMINI",
                "gemini-1.5-flash");
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("question")))
                .build();

        List<ChatStreamEvent> events = adapter.stream(request).collect(Collectors.toList());

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(ChatStreamEventType.ERROR);
        assertThat(events.get(0).errorMessage()).contains("streaming is not supported");
    }

    @Test
    void shouldPropagateSynchronousStreamingFactoryErrors() {
        LangChainChatAdapter adapter = new LangChainChatAdapter(
                request -> messages -> Response.from(AiMessage.from("answer")),
                request -> {
                    throw new IllegalStateException("stream factory failed");
                },
                "OPENAI",
                "gpt-4o-mini");
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("question")))
                .build();

        assertThatThrownBy(() -> adapter.stream(request).collect(Collectors.toList()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stream factory failed");
    }

    @Test
    void shouldRejectEmptyProviderResponses() {
        LangChainChatAdapter adapter = new LangChainChatAdapter(
                request -> messages -> Response.from(AiMessage.from("")),
                null,
                "OPENAI",
                "gpt-4o-mini");
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("question")))
                .build();

        assertThatThrownBy(() -> adapter.chat(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void shouldMergeLeadingSystemMessagesWhenProviderRequiresIt() {
        AtomicReference<List<dev.langchain4j.data.message.ChatMessage>> capturedMessages = new AtomicReference<>();
        LangChainChatAdapter adapter = new LangChainChatAdapter(
                request -> messages -> {
                    capturedMessages.set(messages);
                    return Response.from(AiMessage.from("answer"));
                },
                null,
                "GOOGLE_AI_GEMINI",
                "gemini-1.5-flash",
                true);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("first"),
                        ChatMessage.system("second"),
                        ChatMessage.user("question")))
                .build();

        adapter.chat(request);

        assertThat(capturedMessages.get()).hasSize(2);
        assertThat(capturedMessages.get().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) capturedMessages.get().get(0)).text()).isEqualTo("first\n\nsecond");
    }

    @Test
    void shouldRejectSystemMessagesAfterConversationMessagesWhenProviderRequiresLeadingSystemMessages() {
        LangChainChatAdapter adapter = new LangChainChatAdapter(
                request -> messages -> Response.from(AiMessage.from("answer")),
                null,
                "GOOGLE_AI_GEMINI",
                "gemini-1.5-flash",
                true);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.user("question"),
                        ChatMessage.system("late system")))
                .build();

        assertThatThrownBy(() -> adapter.chat(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only supports system messages before user or assistant messages");
    }
}
