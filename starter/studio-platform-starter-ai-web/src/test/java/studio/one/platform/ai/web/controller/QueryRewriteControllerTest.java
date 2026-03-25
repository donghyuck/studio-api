package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.ai.web.dto.QueryRewriteRequestDto;
import studio.one.platform.ai.web.dto.QueryRewriteResponseDto;
import studio.one.platform.web.dto.ApiResponse;

class QueryRewriteControllerTest {

    private PromptRenderer promptRenderer;
    private ChatPort chatPort;
    private QueryRewriteController controller;

    @BeforeEach
    void setUp() {
        promptRenderer = mock(PromptRenderer.class);
        chatPort = mock(ChatPort.class);
        controller = new QueryRewriteController(promptRenderer, chatPort);
        when(promptRenderer.render(any(), any())).thenReturn("prompt");
    }

    @Test
    void normalizesExpandedQueryAndDeduplicatesKeywordsFromParsedJson() {
        when(chatPort.chat(any())).thenReturn(response("""
                {
                  "original_query": "  hello  ",
                  "expanded_query": "hello, greeting,\\n salutation , hello",
                  "keywords": ["hello", "Greeting", "salutation", "hello"]
                }
                """));

        ApiResponse<QueryRewriteResponseDto> response = controller.rewrite(new QueryRewriteRequestDto("hello"));

        assertThat(response.getData().originalQuery()).isEqualTo("hello");
        assertThat(response.getData().expandedQuery()).isEqualTo("hello, greeting, salutation");
        assertThat(response.getData().keywords()).containsExactly("hello", "Greeting", "salutation");
    }

    @Test
    void derivesKeywordsFromExpandedQueryWhenKeywordsFieldIsMissing() {
        when(chatPort.chat(any())).thenReturn(response("""
                {
                  "original_query": "hello",
                  "expanded_query": "hello, greeting, salutation"
                }
                """));

        ApiResponse<QueryRewriteResponseDto> response = controller.rewrite(new QueryRewriteRequestDto("hello"));

        assertThat(response.getData().expandedQuery()).isEqualTo("hello, greeting, salutation");
        assertThat(response.getData().keywords()).containsExactly("hello", "greeting", "salutation");
    }

    @Test
    void fallsBackToNormalizedRawTermsWhenJsonParsingFails() {
        when(chatPort.chat(any())).thenReturn(response("""
                ```json
                hello, greeting,
                salutation, hello
                ```
                """));

        ApiResponse<QueryRewriteResponseDto> response = controller.rewrite(new QueryRewriteRequestDto("hello"));

        assertThat(response.getData().expandedQuery()).isEqualTo("hello, greeting, salutation");
        assertThat(response.getData().keywords()).containsExactly("hello", "greeting", "salutation");
        assertThat(response.getData().rawResponse()).contains("hello, greeting");
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(ChatMessage.assistant(content)), "test-model", Map.of());
    }
}
