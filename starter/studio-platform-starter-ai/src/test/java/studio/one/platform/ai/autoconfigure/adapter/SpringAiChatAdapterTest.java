package studio.one.platform.ai.autoconfigure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatRequest;

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

        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model);

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
        assertThat(response.metadata()).containsEntry("finishReason", "stop");
        assertThat(response.metadata().get("tokenUsage"))
                .isEqualTo(java.util.Map.of("inputTokens", 5, "outputTokens", 7, "totalTokens", 12));
        assertThat(response.metadata()).containsKeys("chatResponseMetadata", "generationMetadata");
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
}
