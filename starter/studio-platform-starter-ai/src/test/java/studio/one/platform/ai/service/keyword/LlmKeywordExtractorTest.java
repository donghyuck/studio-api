package studio.one.platform.ai.service.keyword;

import org.junit.jupiter.api.Test;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LlmKeywordExtractorTest {

    @Test
    void shouldLimitInputWithConfiguredMaxInputChars() {
        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();
        PromptRenderer promptRenderer = promptRenderer();
        ChatPort chatPort = request -> {
            capturedRequest.set(request);
            return new ChatResponse(List.of(ChatMessage.assistant("[\"keyword\"]")), "test", Map.of());
        };
        LlmKeywordExtractor extractor = new LlmKeywordExtractor(promptRenderer, chatPort, 5);

        extractor.extract("1234567890");

        assertThat(capturedRequest.get().messages().get(1).content()).isEqualTo("12345");
    }

    @Test
    void shouldNormalizeDuplicateKeywordsCaseInsensitively() {
        PromptRenderer promptRenderer = promptRenderer();
        ChatPort chatPort = request -> new ChatResponse(
                List.of(ChatMessage.assistant("[\" Upload \", \"upload\", \"파일\", \" \", null, \"File\"]")),
                "test",
                Map.of());
        LlmKeywordExtractor extractor = new LlmKeywordExtractor(promptRenderer, chatPort, 4_000);

        List<String> keywords = extractor.extract("text");

        assertThat(keywords).containsExactly("Upload", "파일", "File");
    }

    private PromptRenderer promptRenderer() {
        return new PromptRenderer() {
            @Override
            public String render(String name, Map<String, Object> params) {
                return "system prompt";
            }

            @Override
            public String getRawPrompt(String name) {
                return "system prompt";
            }
        };
    }
}
