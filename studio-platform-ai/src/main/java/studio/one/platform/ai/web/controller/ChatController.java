package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.dto.ChatResponseDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("/api/ai/chat")
@Validated
public class ChatController {

    private final ChatPort chatPort;

    public ChatController(ChatPort chatPort) {
        this.chatPort = Objects.requireNonNull(chatPort, "chatPort");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponseDto>> chat(@Valid @RequestBody ChatRequestDto request) {
        ChatResponse response = chatPort.chat(toDomainChatRequest(request));
        return ResponseEntity.ok(ApiResponse.ok(toDto(response)));
    }

    private ChatRequest toDomainChatRequest(ChatRequestDto request) {
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(toDomainMessages(request.messages()));
        if (request.model() != null) {
            builder.model(request.model());
        }
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        if (request.topP() != null) {
            builder.topP(request.topP());
        }
        if (request.topK() != null) {
            builder.topK(request.topK());
        }
        if (request.maxOutputTokens() != null) {
            builder.maxOutputTokens(request.maxOutputTokens());
        }
        if (request.stopSequences() != null && !request.stopSequences().isEmpty()) {
            builder.stopSequences(request.stopSequences());
        }
        return builder.build();
    }

    private List<ChatMessage> toDomainMessages(List<ChatMessageDto> messages) {
        return messages.stream()
                .map(this::toDomainMessage)
                .toList();
    }

    private ChatMessage toDomainMessage(ChatMessageDto dto) {
        ChatMessageRole role = ChatMessageRole.valueOf(dto.role().trim().toUpperCase(Locale.ROOT));
        return new ChatMessage(role, dto.content());
    }

    private ChatResponseDto toDto(ChatResponse response) {
        List<ChatMessageDto> messages = response.messages().stream()
                .map(message -> new ChatMessageDto(message.role().name().toLowerCase(Locale.ROOT), message.content()))
                .toList();
        Map<String, Object> metadata = Map.copyOf(response.metadata());
        return new ChatResponseDto(messages, response.model(), metadata);
    }
}
