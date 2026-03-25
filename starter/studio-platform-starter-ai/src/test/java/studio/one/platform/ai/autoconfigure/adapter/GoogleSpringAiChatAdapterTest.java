package studio.one.platform.ai.autoconfigure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatRequest;

class GoogleSpringAiChatAdapterTest {

    @Test
    void passesGoogleChatOptionsFromChatRequest() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(
                        List.of(new Generation(
                                new AssistantMessage("hello from google"),
                                ChatGenerationMetadata.builder().finishReason("STOP").build())),
                        ChatResponseMetadata.builder()
                                .id("resp-1")
                                .model("gemini-2.5-flash")
                                .usage(new DefaultUsage(5, 7))
                                .build()));

        GoogleSpringAiChatAdapter adapter = new GoogleSpringAiChatAdapter(model);

        studio.one.platform.ai.core.chat.ChatResponse response = adapter.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .model("gemini-2.5-flash")
                .temperature(0.6)
                .topP(0.7)
                .topK(20)
                .maxOutputTokens(100)
                .stopSequences(List.of("STOP"))
                .build());

        assertThat(response.model()).isEqualTo("gemini-2.5-flash");

        org.mockito.ArgumentCaptor<Prompt> promptCaptor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(model).call(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        assertThat(prompt.getOptions()).isInstanceOf(GoogleGenAiChatOptions.class);
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(options.getTemperature()).isEqualTo(0.6);
        assertThat(options.getTopP()).isEqualTo(0.7);
        assertThat(options.getTopK()).isEqualTo(20);
        assertThat(options.getMaxOutputTokens()).isEqualTo(100);
        assertThat(options.getStopSequences()).containsExactly("STOP");
    }
}
