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
import org.springframework.ai.chat.prompt.Prompt;

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
                                .usage(new DefaultUsage(3, 4, 7))
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
        assertThat(response.metadata()).containsEntry("tokenUsage", new DefaultUsage(3, 4, 7));
        assertThat(response.metadata()).containsKeys("chatResponseMetadata", "generationMetadata");
    }

    @Test
    void passesRequestOptionsToPrompt() {
        ChatModel model = mock(ChatModel.class);
        Prompt[] captured = new Prompt[1];
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage("configured"),
                            ChatGenerationMetadata.builder().finishReason("stop").build())),
                    ChatResponseMetadata.builder().model("gemini-2.5-flash").build());
        });

        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model);

        adapter.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.system("rules"), ChatMessage.user("hello")))
                .model("gemini-2.5-flash")
                .temperature(0.4)
                .topP(0.8)
                .topK(32)
                .maxOutputTokens(256)
                .stopSequences(List.of("END"))
                .build());

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getOptions()).isNotNull();
        assertThat(captured[0].getOptions().getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(captured[0].getOptions().getTemperature()).isEqualTo(0.4);
        assertThat(captured[0].getOptions().getTopP()).isEqualTo(0.8);
        assertThat(captured[0].getOptions().getTopK()).isEqualTo(32);
        assertThat(captured[0].getOptions().getMaxTokens()).isEqualTo(256);
        assertThat(captured[0].getOptions().getStopSequences()).containsExactly("END");
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
