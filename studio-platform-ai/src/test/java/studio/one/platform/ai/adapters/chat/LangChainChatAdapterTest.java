package studio.one.platform.ai.adapters.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LangChainChatAdapterTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private LangChainChatAdapter adapter;

    @Captor
    private ArgumentCaptor<ChatRequest> requestCaptor;

    @Test
    void shouldForwardRequestAndMapResponse() {
        TokenUsage tokenUsage = new TokenUsage(5, 7, 12);
        ChatResponse providerResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("assistant reply"))
                .metadata(ChatResponseMetadata.builder()
                        .id("response-id")
                        .modelName("gpt-4o-mini")
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(providerResponse);

        studio.one.platform.ai.core.chat.ChatRequest request = studio.one.platform.ai.core.chat.ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("system instructions"),
                        ChatMessage.user("hello")))
                .model("gpt-4o-mini")
                .temperature(0.6)
                .maxOutputTokens(100)
                .stopSequences(List.of("STOP"))
                .build();

        studio.one.platform.ai.core.chat.ChatResponse response = adapter.chat(request);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(response.messages().get(0).content()).isEqualTo("assistant reply");
        assertThat(response.metadata()).containsEntry("responseId", "response-id");
        assertThat(response.metadata()).containsEntry("modelName", "gpt-4o-mini");
        assertThat(response.metadata().get("finishReason")).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata().get("tokenUsage")).isEqualTo(tokenUsage);

        verify(chatModel).chat(requestCaptor.capture());
        ChatRequest captured = requestCaptor.getValue();
        assertThat(captured.messages()).hasSize(2);
        assertThat(captured.parameters().modelName()).isEqualTo("gpt-4o-mini");
        assertThat(captured.parameters().temperature()).isEqualTo(0.6);
        assertThat(captured.parameters().maxOutputTokens()).isEqualTo(100);
        assertThat(captured.parameters().stopSequences()).containsExactly("STOP");
    }
}
