package studio.one.platform.ai.service.cleaning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;

@ExtendWith(MockitoExtension.class)
class LlmTextCleanerTest {

    @Mock
    private PromptRenderer promptRenderer;

    @Mock
    private ChatPort chatPort;

    @Test
    void shouldParseCleanTextFromJsonResponse() {
        LlmTextCleaner cleaner = cleaner(true);
        when(promptRenderer.render(eq("rag-cleaner"), any(Map.class))).thenReturn("clean prompt");
        when(chatPort.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(
                List.of(ChatMessage.assistant("""
                        ```json
                        {"clean_text":"정제된 본문"}
                        ```
                        """)),
                "test-model",
                Map.of()));

        TextCleaningResult result = cleaner.clean("원문");

        assertThat(result.text()).isEqualTo("정제된 본문");
        assertThat(result.cleaned()).isTrue();
        assertThat(result.cleanerPrompt()).isEqualTo("rag-cleaner");
    }

    @Test
    void shouldFallbackToOriginalTextWhenFailOpen() {
        LlmTextCleaner cleaner = cleaner(true);
        when(promptRenderer.render(eq("rag-cleaner"), any(Map.class))).thenReturn("clean prompt");
        when(chatPort.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("provider failure"));

        TextCleaningResult result = cleaner.clean("원문");

        assertThat(result.text()).isEqualTo("원문");
        assertThat(result.cleaned()).isFalse();
        assertThat(result.cleanerPrompt()).isEqualTo("rag-cleaner");
    }

    @Test
    void shouldThrowWhenFailClosed() {
        LlmTextCleaner cleaner = cleaner(false);
        when(promptRenderer.render(eq("rag-cleaner"), any(Map.class))).thenReturn("clean prompt");
        when(chatPort.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(
                List.of(ChatMessage.assistant("{\"unexpected\":\"value\"}")),
                "test-model",
                Map.of()));

        assertThatThrownBy(() -> cleaner.clean("원문"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to clean RAG text");
    }

    @Test
    void shouldTrimInputBeforeRenderingPrompt() {
        LlmTextCleaner cleaner = new LlmTextCleaner(promptRenderer, chatPort, new ObjectMapper(),
                "rag-cleaner", 4, true);
        when(promptRenderer.render(eq("rag-cleaner"), any(Map.class))).thenReturn("clean prompt");
        when(chatPort.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(
                List.of(ChatMessage.assistant("{\"clean_text\":\"abcd\"}")),
                "test-model",
                Map.of()));

        cleaner.clean("abcdef");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(promptRenderer).render(eq("rag-cleaner"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue()).containsEntry("text", "abcd");
    }

    private LlmTextCleaner cleaner(boolean failOpen) {
        return new LlmTextCleaner(promptRenderer, chatPort, new ObjectMapper(),
                "rag-cleaner", 20_000, failOpen);
    }
}
