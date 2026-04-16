package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;

class ChatControllerTest {

    @Mock
    private AiProviderRegistry providerRegistry;

    @Mock
    private ChatPort defaultChatPort;

    @Mock
    private ChatPort googleChatPort;

    @Mock
    private RagPipelineService ragPipelineService;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ChatController(providerRegistry, ragPipelineService);
        when(providerRegistry.chatPort(null)).thenReturn(defaultChatPort);
        when(providerRegistry.chatPort("google")).thenReturn(googleChatPort);
        when(defaultChatPort.chat(any())).thenReturn(response("default"));
        when(googleChatPort.chat(any())).thenReturn(response("google"));
    }

    @Test
    void chatUsesRequestedProvider() {
        controller.chat(new ChatRequestDto(
                "google",
                null,
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null));

        verify(providerRegistry).chatPort("google");
        verify(googleChatPort).chat(any(ChatRequest.class));
    }

    @Test
    void chatUsesDefaultProviderWhenProviderMissing() {
        controller.chat(new ChatRequestDto(
                null,
                null,
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null));

        verify(providerRegistry).chatPort(null);
        verify(defaultChatPort).chat(any(ChatRequest.class));
    }

    @Test
    void chatTreatsBlankProviderAsDefaultProvider() {
        controller.chat(new ChatRequestDto(
                "  ",
                null,
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null));

        verify(providerRegistry).chatPort(null);
        verify(defaultChatPort).chat(any(ChatRequest.class));
    }

    @Test
    void chatTrimsRequestedProvider() {
        controller.chat(new ChatRequestDto(
                " google ",
                null,
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null));

        verify(providerRegistry).chatPort("google");
        verify(googleChatPort).chat(any(ChatRequest.class));
    }

    @Test
    void chatRejectsUnknownProviderAsBadRequest() {
        when(providerRegistry.chatPort("missing")).thenThrow(new IllegalArgumentException("Unknown provider: missing"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDto(
                        "missing",
                        null,
                        List.of(new ChatMessageDto("user", "hello")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("missing");
    }

    @Test
    void chatPrependsSystemPrompt() {
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);

        controller.chat(new ChatRequestDto(
                null,
                "answer briefly",
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null));

        verify(defaultChatPort).chat(captor.capture());
        assertThat(captor.getValue().messages()).hasSize(2);
        assertThat(captor.getValue().messages().get(0).role().name()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().messages().get(0).content()).isEqualTo("answer briefly");
        assertThat(captor.getValue().messages().get(1).role().name()).isEqualTo("USER");
    }

    @Test
    void ragChatAddsContextAndClientSystemPromptAndSearchesByObject() {
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.searchByObject(any(RagSearchRequest.class), any(), any()))
                .thenReturn(List.of(new RagSearchResult("doc-1", "file text", Map.of(), 0.9d)));

        controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        "google",
                        "answer from file",
                        List.of(new ChatMessageDto("user", "summarize")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "summary",
                3,
                "attachment",
                "123"));

        verify(ragPipelineService).searchByObject(any(RagSearchRequest.class), org.mockito.Mockito.eq("attachment"),
                org.mockito.Mockito.eq("123"));
        verify(googleChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages()).hasSize(3);
        assertThat(chatCaptor.getValue().messages().get(0).role().name()).isEqualTo("SYSTEM");
        assertThat(chatCaptor.getValue().messages().get(0).content()).contains("file text");
        assertThat(chatCaptor.getValue().messages().get(1).role().name()).isEqualTo("SYSTEM");
        assertThat(chatCaptor.getValue().messages().get(1).content()).isEqualTo("answer from file");
        assertThat(chatCaptor.getValue().messages().get(2).role().name()).isEqualTo("USER");
    }

    @Test
    void ragChatLimitsContextChunks() {
        controller = new ChatController(providerRegistry, ragPipelineService, new RagContextBuilder(2, 12_000, true));
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(
                        new RagSearchResult("doc-1", "first", Map.of(), 0.9d),
                        new RagSearchResult("doc-2", "second", Map.of(), 0.8d),
                        new RagSearchResult("doc-3", "third", Map.of(), 0.7d)));

        controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "summarize")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "summary",
                3,
                null,
                null));

        verify(defaultChatPort).chat(chatCaptor.capture());
        String context = chatCaptor.getValue().messages().get(0).content();
        assertThat(context).contains("doc-1", "doc-2");
        assertThat(context).doesNotContain("doc-3");
    }

    @Test
    void ragChatLimitsContextCharacters() {
        controller = new ChatController(providerRegistry, ragPipelineService, new RagContextBuilder(8, 80, true));
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult(
                        "doc-1",
                        "0123456789".repeat(20),
                        Map.of(),
                        0.9d)));

        controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "summarize")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "summary",
                3,
                null,
                null));

        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .isEqualTo("참고할 문서가 없습니다. 일반적으로 답변하세요.");
    }

    @Test
    void ragChatCanOmitScoresFromContext() {
        controller = new ChatController(providerRegistry, ragPipelineService, new RagContextBuilder(8, 12_000, false));
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "first", Map.of(), 0.9d)));

        controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "summarize")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "summary",
                3,
                null,
                null));

        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("docId=doc-1")
                .doesNotContain("score=");
    }

    @Test
    void ragChatListsByObjectWhenQueryMissingAndObjectFilterPresent() {
        when(ragPipelineService.listByObject("attachment", "123", 3))
                .thenReturn(List.of(new RagSearchResult("doc-1", "file text", Map.of(), 1.0d)));

        controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "summarize")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                3,
                "attachment",
                "123"));

        verify(ragPipelineService).listByObject("attachment", "123", 3);
    }

    @Test
    void ragChatRejectsObjectIdWithoutObjectType() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.chatWithRag(new ChatRagRequestDto(
                        new ChatRequestDto(
                                null,
                                null,
                                List.of(new ChatMessageDto("user", "summarize")),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        null,
                        3,
                        null,
                        "123")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(ragPipelineService);
    }

    @Test
    void ragChatRejectsObjectTypeWithoutObjectId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.chatWithRag(new ChatRagRequestDto(
                        new ChatRequestDto(
                                null,
                                null,
                                List.of(new ChatMessageDto("user", "summarize")),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        "summary",
                        3,
                        "attachment",
                        null)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(ragPipelineService);
    }

    @Test
    void ragChatRejectsUnsupportedObjectType() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.chatWithRag(new ChatRagRequestDto(
                        new ChatRequestDto(
                                null,
                                null,
                                List.of(new ChatMessageDto("user", "summarize")),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        null,
                        3,
                        "document",
                        "123")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(ragPipelineService);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(studio.one.platform.ai.core.chat.ChatMessage.assistant(content)), "model",
                Map.of());
    }
}
