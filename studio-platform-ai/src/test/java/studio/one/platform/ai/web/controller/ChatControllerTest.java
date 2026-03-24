package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.dto.ChatResponseDto;
import studio.one.platform.web.dto.ApiResponse;

class ChatControllerTest {

    @Test
    void delegatesDefaultChatRequestsToInjectedChatPort() {
        ChatPort chatPort = mock(ChatPort.class);
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);
        ChatController controller = new ChatController(chatPort, ragPipelineService);

        ChatResponse chatResponse = new ChatResponse(
                List.of(ChatMessage.assistant("hello from spring ai alias")),
                "gpt-4o-mini",
                Map.of("provider", "openai-springai"));
        when(chatPort.chat(any())).thenReturn(chatResponse);

        ChatRequestDto request = new ChatRequestDto(
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null);

        ResponseEntity<ApiResponse<ChatResponseDto>> response = controller.chat(request);

        verify(chatPort).chat(any());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        ChatResponseDto body = response.getBody().getData();
        assertThat(body.model()).isEqualTo("gpt-4o-mini");
        assertThat(body.messages()).extracting(ChatMessageDto::content).containsExactly("hello from spring ai alias");
        assertThat(body.metadata()).containsEntry("provider", "openai-springai");
    }
}
