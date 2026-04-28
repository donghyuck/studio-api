package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.autoconfigure.AiWebChatProperties;
import studio.one.platform.ai.core.chat.ChatMemoryStore;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.chat.ChatResponseMetadata;
import studio.one.platform.ai.core.chat.ChatStreamEvent;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.ChatMemoryOptionsDto;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.dto.ChatResponseDto;
import studio.one.platform.ai.web.dto.ConversationActionRequestDto;
import studio.one.platform.ai.web.dto.ConversationDetailDto;
import studio.one.platform.ai.web.dto.ConversationMessageActionRequestDto;
import studio.one.platform.ai.web.dto.ConversationSummaryDto;
import studio.one.platform.ai.web.service.ConversationChatService;
import studio.one.platform.ai.web.service.InMemoryChatMemoryStore;
import studio.one.platform.ai.web.service.InMemoryConversationRepository;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.web.dto.ApiResponse;

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
        when(ragPipelineService.latestDiagnostics()).thenReturn(Optional.empty());
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
    void chatMapsProviderPromptValidationFailureToBadRequest() {
        when(googleChatPort.chat(any(ChatRequest.class)))
                .thenThrow(new IllegalArgumentException("Google GenAI supports only leading system messages"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDto(
                        "google",
                        null,
                        List.of(new ChatMessageDto("user", "hello")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("leading system messages");
    }

    @Test
    void streamMapsProviderPromptValidationFailureToBadRequest() {
        when(googleChatPort.stream(any(ChatRequest.class)))
                .thenThrow(new IllegalArgumentException("Google GenAI supports only leading system messages"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.stream(new ChatRequestDto(
                        "google",
                        null,
                        List.of(new ChatMessageDto("user", "hello")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null), null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("leading system messages");
    }

    @Test
    void ragChatRequiresChatRagAndObjectScopeReadAuthorities() throws Exception {
        String expression = preAuthorizeValue(ChatController.class.getMethod(
                "chatWithRag",
                ChatRagRequestDto.class,
                java.security.Principal.class));

        assertThat(expression).contains("services:ai_chat','write");
        assertThat(expression).contains("services:ai_rag','read");
        assertThat(expression).contains("features:attachment','read");
        assertThat(expression).contains("equalsIgnoreCase('attachment')");
        assertThat(expression).contains("objects:' + #request.objectType().trim() + ':'");
        assertThat(expression).contains("objects:' + #request.objectType().trim()");
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
    void chatRejectsMemoryRequestWhenServerMemoryIsDisabled() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "hello")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ChatMemoryOptionsDto(true, "chat-1"))));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("not enabled");
        verifyNoInteractions(defaultChatPort);
    }

    @Test
    void chatRejectsBlankConversationIdWhenMemoryIsEnabled() {
        controller = memoryController();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "hello")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ChatMemoryOptionsDto(true, "  "))));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("conversationId");
        verifyNoInteractions(defaultChatPort);
    }

    @Test
    void chatAddsPreviousConversationMessagesWhenMemoryIsEnabled() {
        controller = memoryController();
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);

        controller.chat(memoryChat("chat-1", "hello"));
        ChatResponseDto response = controller.chat(memoryChat("chat-1", "next")).getBody().getData();

        verify(defaultChatPort, times(2)).chat(captor.capture());
        List<studio.one.platform.ai.core.chat.ChatMessage> secondMessages = captor.getAllValues().get(1).messages();
        assertThat(secondMessages)
                .extracting(message -> message.role().name() + ":" + message.content())
                .containsExactly("USER:hello", "ASSISTANT:default", "USER:next");
        assertThat(response.metadata())
                .containsEntry("memoryEnabled", true)
                .containsEntry("memoryUsed", true)
                .containsEntry("conversationId", "chat-1")
                .containsEntry("memoryMessageCount", 4);
    }

    @Test
    void streamWritesSseEventsWithRequestId() throws Exception {
        when(defaultChatPort.stream(any(ChatRequest.class))).thenReturn(Stream.of(
                ChatStreamEvent.delta("hel", "model", ChatResponseMetadata.empty()),
                ChatStreamEvent.delta("lo", "model", ChatResponseMetadata.empty()),
                ChatStreamEvent.usage(ChatResponseMetadata.empty()),
                ChatStreamEvent.complete("model", ChatResponseMetadata.empty())));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        controller.stream(new ChatRequestDto(
                null,
                null,
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null), null).getBody().writeTo(output);

        String body = output.toString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("event: delta")
                .contains("event: usage")
                .contains("event: complete")
                .contains("\"requestId\"");
    }

    @Test
    void streamDoesNotStorePartialAssistantWhenErrorEventOccurs() throws Exception {
        controller = conversationController();
        when(defaultChatPort.stream(any(ChatRequest.class))).thenReturn(Stream.of(
                ChatStreamEvent.delta("partial", "model", ChatResponseMetadata.empty()),
                ChatStreamEvent.error("provider failed", ChatResponseMetadata.empty())));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        controller.stream(memoryChat("chat-1", "hello"), null).getBody().writeTo(output);

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("event: error");
        assertThat(controller.conversations(0, 20, null).getBody().getData()).isEmpty();
    }

    @Test
    void streamFlushesErrorEventWhenIteratorFails() throws Exception {
        when(defaultChatPort.stream(any(ChatRequest.class))).thenReturn(Stream.generate(() -> {
            throw new IllegalStateException("provider failed");
        }));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        controller.stream(new ChatRequestDto(
                null,
                null,
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null), null).getBody().writeTo(output);

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("event: error")
                .contains("provider failed");
    }

    @Test
    void conversationApisListDetailAndDeleteMemoryConversation() {
        controller = conversationController();

        controller.chat(memoryChat("chat-1", "hello"));

        List<ConversationSummaryDto> conversations = controller.conversations(0, 20, null)
                .getBody()
                .getData();
        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).conversationId()).isEqualTo("chat-1");
        assertThat(conversations.get(0).messageCount()).isEqualTo(2);

        ConversationDetailDto detail = controller.conversation("chat-1", null).getBody().getData();
        assertThat(detail.messages())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly("user:hello", "assistant:default");

        assertThat(controller.deleteConversation("chat-1", null).getBody().getData())
                .containsEntry("deleted", true);
        assertThat(controller.conversations(0, 20, null).getBody().getData()).isEmpty();
    }

    @Test
    void conversationApisKeepPrincipalScopesSeparate() {
        controller = conversationController();

        controller.chat(memoryChat("chat-1", "hello from user a"), () -> "user-a");
        controller.chat(memoryChat("chat-1", "hello from user b"), () -> "user-b");

        ConversationDetailDto userA = controller.conversation("chat-1", () -> "user-a").getBody().getData();
        ConversationDetailDto userB = controller.conversation("chat-1", () -> "user-b").getBody().getData();

        assertThat(userA.messages())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly("user:hello from user a", "assistant:default");
        assertThat(userB.messages())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly("user:hello from user b", "assistant:default");
    }

    @Test
    void conversationDetailCanReadBeyondSingleRepositoryPage() {
        controller = conversationController();

        for (int i = 0; i < 251; i++) {
            controller.chat(memoryChat("chat-1", "hello " + i));
        }

        ConversationDetailDto detail = controller.conversation("chat-1", null).getBody().getData();
        assertThat(detail.messages()).hasSize(502);
    }

    @Test
    void regenerateReplacesLastAssistantResponse() {
        controller = conversationController();
        when(defaultChatPort.chat(any())).thenReturn(response("first"), response("regenerated"));

        controller.chat(memoryChat("chat-1", "hello"));
        ChatResponseDto regenerated = controller.regenerate(
                new ConversationActionRequestDto("chat-1", null, null, null, null),
                null).getBody().getData();

        assertThat(regenerated.messages().get(0).content()).isEqualTo("regenerated");
        ConversationDetailDto detail = controller.conversation("chat-1", null).getBody().getData();
        assertThat(detail.messages())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly("user:hello", "assistant:regenerated");
    }

    @Test
    void regenerateAppendsAssistantWhenLastUserHasNoAssistantYet() {
        controller = conversationController();
        when(defaultChatPort.chat(any())).thenReturn(response("first"), response("second"), response("regenerated"));
        controller.chat(memoryChat("chat-1", "hello"));
        controller.chat(memoryChat("chat-1", "next"));
        ConversationDetailDto before = controller.conversation("chat-1", null).getBody().getData();
        String secondUserId = before.messages().get(2).messageId();

        controller.truncate(new ConversationMessageActionRequestDto("chat-1", secondUserId, null), null);
        controller.regenerate(new ConversationActionRequestDto("chat-1", null, null, null, null), null);

        ConversationDetailDto detail = controller.conversation("chat-1", null).getBody().getData();
        assertThat(detail.messages())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly("user:hello", "assistant:first", "user:next", "assistant:regenerated");
    }

    @Test
    void truncateForkCompactAndCancelConversation() {
        controller = conversationController();
        controller.chat(memoryChat("chat-1", "hello"));
        controller.chat(memoryChat("chat-1", "next"));
        ConversationDetailDto detail = controller.conversation("chat-1", null).getBody().getData();
        String firstMessageId = detail.messages().get(0).messageId();

        ConversationDetailDto forked = controller.fork(
                new ConversationMessageActionRequestDto("chat-1", firstMessageId, "chat-copy"),
                null).getBody().getData();
        assertThat(forked.conversationId()).isEqualTo("chat-copy");
        assertThat(forked.messages()).hasSize(1);

        ConversationDetailDto truncated = controller.truncate(
                new ConversationMessageActionRequestDto("chat-1", firstMessageId, null),
                null).getBody().getData();
        assertThat(truncated.messages()).hasSize(1);

        ConversationDetailDto compacted = controller.compact(
                new ConversationActionRequestDto("chat-1", null, null, "short summary", null),
                null).getBody().getData();
        assertThat(compacted.status()).isEqualTo("compacted");
        assertThat(compacted.summary()).isEqualTo("short summary");

        ConversationDetailDto cancelled = controller.cancel(
                new ConversationActionRequestDto("chat-1", null, null, null, null),
                null).getBody().getData();
        assertThat(cancelled.status()).isEqualTo("cancelled");
    }

    @Test
    void chatDoesNotAppendMemoryWhenProviderFails() {
        ChatMemoryStore memoryStore = memoryStore();
        controller = new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(),
                false, memoryStore, true);
        when(defaultChatPort.chat(any())).thenThrow(new IllegalStateException("provider failed"));

        assertThrows(IllegalStateException.class, () -> controller.chat(memoryChat("chat-1", "hello")));

        assertThat(memoryStore.get("anonymous:chat-1")).isEmpty();
    }

    @Test
    void chatRejectsBlankPrincipalNameWhenMemoryIsEnabled() {
        controller = memoryController();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.chat(memoryChat("chat-1", "hello"), () -> "  "));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("Principal name");
        verifyNoInteractions(defaultChatPort);
    }

    @Test
    void differentPrincipalsDoNotShareConversationMemory() {
        controller = memoryController();
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);

        controller.chat(memoryChat("chat-1", "hello from user a"), () -> "user-a");
        controller.chat(memoryChat("chat-1", "hello from user b"), () -> "user-b");

        verify(defaultChatPort, times(2)).chat(captor.capture());
        List<studio.one.platform.ai.core.chat.ChatMessage> userBMessages = captor.getAllValues().get(1).messages();
        assertThat(userBMessages)
                .extracting(message -> message.role().name() + ":" + message.content())
                .containsExactly("USER:hello from user b");
    }

    @Test
    void ragChatStoresOnlyConversationMessagesWhenMemoryIsEnabled() {
        controller = memoryController();
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "file text", Map.of(), 0.9d)));

        controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        "answer from file",
                        List.of(new ChatMessageDto("user", "summarize")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ChatMemoryOptionsDto(true, "chat-1")),
                "summary",
                3,
                "attachment",
                "123"));
        controller.chat(memoryChat("chat-1", "follow up"));

        verify(defaultChatPort, times(2)).chat(captor.capture());
        List<studio.one.platform.ai.core.chat.ChatMessage> secondMessages = captor.getAllValues().get(1).messages();
        assertThat(secondMessages)
                .extracting(message -> message.role().name() + ":" + message.content())
                .containsExactly("USER:summarize", "ASSISTANT:default", "USER:follow up");
        assertThat(secondMessages)
                .extracting(studio.one.platform.ai.core.chat.ChatMessage::content)
                .doesNotContain("answer from file")
                .noneMatch(content -> content.contains("file text"));
    }

    @Test
    void ragChatAddsContextAndClientSystemPromptAndSearchesByObject() {
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        ArgumentCaptor<RagSearchRequest> ragCaptor = ArgumentCaptor.forClass(RagSearchRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
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

        verify(ragPipelineService).search(ragCaptor.capture());
        assertThat(ragCaptor.getValue().metadataFilter().objectType()).isEqualTo("attachment");
        assertThat(ragCaptor.getValue().metadataFilter().objectId()).isEqualTo("123");
        verify(googleChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages()).hasSize(2);
        assertThat(chatCaptor.getValue().messages().get(0).role().name()).isEqualTo("SYSTEM");
        assertThat(chatCaptor.getValue().messages().get(0).content()).contains("file text");
        assertThat(chatCaptor.getValue().messages().get(0).content()).contains("answer from file");
        assertThat(chatCaptor.getValue().messages().get(1).role().name()).isEqualTo("USER");
    }

    @Test
    void ragChatAllowsNonAttachmentObjectScope() {
        ArgumentCaptor<RagSearchRequest> ragCaptor = ArgumentCaptor.forClass(RagSearchRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "file text", Map.of(), 0.9d)));

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
                "2001",
                "6"));

        verify(ragPipelineService).search(ragCaptor.capture());
        assertThat(ragCaptor.getValue().metadataFilter().objectType()).isEqualTo("2001");
        assertThat(ragCaptor.getValue().metadataFilter().objectId()).isEqualTo("6");
    }

    @Test
    void ragChatUsesObjectScopedCandidatesForContextExpansion() {
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, TestWindowChunkContextExpander.asList()));
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 0.9d)));
        when(ragPipelineService.listByObject("attachment", "123", 12))
                .thenReturn(List.of(
                        new RagSearchResult("chunk-1", "previous",
                                chunkMetadata("chunk-1", null, "chunk-2", 0), 1.0d),
                        new RagSearchResult("chunk-2", "seed",
                                chunkMetadata("chunk-2", "chunk-1", "chunk-3", 1), 1.0d),
                        new RagSearchResult("chunk-3", "next",
                                chunkMetadata("chunk-3", "chunk-2", null, 2), 1.0d)));

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
                "attachment",
                "123"));

        verify(ragPipelineService).listByObject("attachment", "123", 12);
        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("previous\nseed\nnext")
                .contains("docId=chunk-2")
                .contains("score=0.900");
    }

    @Test
    void ragChatUsesNonAttachmentObjectScopedCandidatesForContextExpansion() {
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, TestWindowChunkContextExpander.asList()));
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 0.9d)));
        when(ragPipelineService.listByObject("2001", "6", 12))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 1.0d)));

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
                "2001",
                "6"));

        verify(ragPipelineService).listByObject("2001", "6", 12);
    }

    @Test
    void ragChatUsesConfiguredCandidateMultiplierForContextExpansion() {
        AiWebRagProperties.ExpansionProperties expansion = new AiWebRagProperties.ExpansionProperties();
        expansion.setCandidateMultiplier(2);
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, expansion, TestWindowChunkContextExpander.asList()),
                false,
                null,
                false,
                new ConversationChatService(new InMemoryConversationRepository()),
                Jackson2ObjectMapperBuilder.json().build(),
                expansion.getCandidateMultiplier());
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 0.9d)));
        when(ragPipelineService.listByObject("attachment", "123", 6))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 1.0d)));

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
                "attachment",
                "123"));

        verify(ragPipelineService).listByObject("attachment", "123", 6);
    }

    @Test
    void ragChatCapsContextExpansionCandidateLimit() {
        AiWebRagProperties.ExpansionProperties expansion = new AiWebRagProperties.ExpansionProperties();
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, expansion, TestWindowChunkContextExpander.asList()),
                false,
                null,
                false,
                new ConversationChatService(new InMemoryConversationRepository()),
                Jackson2ObjectMapperBuilder.json().build(),
                1_000,
                25);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 0.9d)));
        when(ragPipelineService.listByObject("attachment", "123", 25))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 1.0d)));

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
                100,
                "attachment",
                "123"));

        verify(ragPipelineService).listByObject("attachment", "123", 25);
    }

    @Test
    void ragChatClampsExcessiveContextExpansionCandidateSettings() {
        AiWebRagProperties.ExpansionProperties expansion = new AiWebRagProperties.ExpansionProperties();
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, expansion, TestWindowChunkContextExpander.asList()),
                false,
                null,
                false,
                new ConversationChatService(new InMemoryConversationRepository()),
                Jackson2ObjectMapperBuilder.json().build(),
                Integer.MAX_VALUE,
                Integer.MAX_VALUE);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 0.9d)));
        when(ragPipelineService.listByObject("attachment", "123", 500))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 1.0d)));

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
                Integer.MAX_VALUE,
                "attachment",
                "123"));

        verify(ragPipelineService).listByObject("attachment", "123", 500);
    }

    @SuppressWarnings("deprecation")
    @Test
    void ragChatKeepsExpansionPropertiesConstructorForCompatibility() {
        AiWebRagProperties.ExpansionProperties expansion = new AiWebRagProperties.ExpansionProperties();
        expansion.setCandidateMultiplier(5);
        expansion.setMaxCandidates(7);
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, expansion, TestWindowChunkContextExpander.asList()),
                false,
                null,
                false,
                new ConversationChatService(new InMemoryConversationRepository()),
                Jackson2ObjectMapperBuilder.json().build(),
                expansion);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 0.9d)));
        when(ragPipelineService.listByObject("attachment", "123", 7))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed", chunkMetadata("chunk-2"), 1.0d)));

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
                "attachment",
                "123"));

        verify(ragPipelineService).listByObject("attachment", "123", 7);
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
    void ragChatDoesNotExposeDiagnosticsWhenClientDebugIsDisabled() {
        controller = new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), true);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "sensitive file body", Map.of(), 0.9d)));
        when(ragPipelineService.latestDiagnostics()).thenReturn(Optional.of(diagnostics()));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
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
                null,
                false)).getBody().getData();

        assertThat(response.metadata()).doesNotContainKey("ragDiagnostics");
        assertThat(response.metadata()).doesNotContainKey("ragContextDiagnostics");
    }

    @Test
    void ragChatDoesNotExposeDiagnosticsWhenServerDebugIsDisabled() {
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "sensitive file body", Map.of(), 0.9d)));
        when(ragPipelineService.latestDiagnostics()).thenReturn(Optional.of(diagnostics()));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
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
                null,
                true)).getBody().getData();

        assertThat(response.metadata()).doesNotContainKey("ragDiagnostics");
        assertThat(response.metadata()).doesNotContainKey("ragContextDiagnostics");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragChatExposesSafeDiagnosticsWhenClientAndServerDebugAreEnabled() {
        controller = new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), true);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "sensitive file body", Map.of(), 0.9d)));
        when(ragPipelineService.latestDiagnostics()).thenReturn(Optional.of(diagnostics()));

        ResponseEntity<ApiResponse<ChatResponseDto>> response = controller.chatWithRag(new ChatRagRequestDto(
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
                null,
                true));

        Map<String, Object> ragDiagnostics = (Map<String, Object>) response.getBody()
                .getData()
                .metadata()
                .get("ragDiagnostics");
        assertThat(ragDiagnostics)
                .containsEntry("strategy", "hybrid")
                .containsEntry("initialResultCount", 1)
                .containsEntry("finalResultCount", 1)
                .containsEntry("topK", 3)
                .doesNotContainKeys("content", "snippet", "text", "chunk");
        assertThat(ragDiagnostics.values()).doesNotContain("sensitive file body");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragChatExposesSafeContextExpansionDiagnosticsWhenDebugIsAllowed() {
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, TestWindowChunkContextExpander.asList()), true);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed sensitive body",
                        chunkMetadata("chunk-2"), 0.9d)));
        when(ragPipelineService.listByObject("attachment", "123", 12))
                .thenReturn(List.of(
                        new RagSearchResult("chunk-1", "previous sensitive body",
                                chunkMetadata("chunk-1", null, "chunk-2", 0), 1.0d),
                        new RagSearchResult("chunk-2", "seed sensitive body",
                                chunkMetadata("chunk-2", "chunk-1", "chunk-3", 1), 1.0d),
                        new RagSearchResult("chunk-3", "next sensitive body",
                                chunkMetadata("chunk-3", "chunk-2", null, 2), 1.0d)));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
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
                "123",
                true)).getBody().getData();

        Map<String, Object> contextDiagnostics = (Map<String, Object>) response.metadata()
                .get("ragContextDiagnostics");
        assertThat(contextDiagnostics)
                .containsEntry("expansionSupported", true)
                .containsEntry("applied", true)
                .containsEntry("strategy", "window")
                .containsEntry("expandedHitCount", 1)
                .containsEntry("candidateCount", 3)
                .containsEntry("resultCount", 1)
                .doesNotContainKeys("content", "snippet", "text", "chunk");
        assertThat(contextDiagnostics.values()).doesNotContain("seed sensitive body", "previous sensitive body");
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
    void ragChatListsByNonAttachmentObjectWhenQueryMissingAndObjectFilterPresent() {
        when(ragPipelineService.listByObject("2001", "6", 3))
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
                "2001",
                "6"));

        verify(ragPipelineService).listByObject("2001", "6", 3);
    }

    @Test
    void ragChatLimitsObjectListResultsBeforeBuildingContext() {
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.listByObject("attachment", "123", 2))
                .thenReturn(List.of(
                        new RagSearchResult("doc-1", "file text 1", Map.of(), 1.0d),
                        new RagSearchResult("doc-2", "file text 2", Map.of(), 1.0d),
                        new RagSearchResult("doc-3", "file text 3", Map.of(), 1.0d)));

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
                2,
                "attachment",
                "123"));

        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("file text 1", "file text 2")
                .doesNotContain("file text 3");
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
    private ChatResponse response(String content) {
        return new ChatResponse(List.of(studio.one.platform.ai.core.chat.ChatMessage.assistant(content)), "model",
                Map.of());
    }

    private ChatController memoryController() {
        return new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), false,
                memoryStore(), true);
    }

    private ChatController conversationController() {
        return new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), false,
                memoryStore(), true, new ConversationChatService(new InMemoryConversationRepository()));
    }

    private ChatMemoryStore memoryStore() {
        AiWebChatProperties.MemoryProperties properties = new AiWebChatProperties.MemoryProperties();
        properties.setMaxMessages(20);
        return new InMemoryChatMemoryStore(properties);
    }

    private ChatRequestDto memoryChat(String conversationId, String message) {
        return new ChatRequestDto(
                null,
                null,
                List.of(new ChatMessageDto("user", message)),
                null,
                null,
                null,
                null,
                null,
                null,
                new ChatMemoryOptionsDto(true, conversationId));
    }

    private RagRetrievalDiagnostics diagnostics() {
        return new RagRetrievalDiagnostics(
                RagRetrievalDiagnostics.Strategy.HYBRID,
                1,
                1,
                0.15d,
                0.7d,
                0.3d,
                null,
                null,
                3);
    }

    private static String preAuthorizeValue(java.lang.reflect.Method method) {
        return java.util.Arrays.stream(method.getAnnotations())
                .filter(annotation -> "org.springframework.security.access.prepost.PreAuthorize"
                        .equals(annotation.annotationType().getName()))
                .findFirst()
                .map(annotation -> {
                    try {
                        return (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    } catch (ReflectiveOperationException ex) {
                        throw new AssertionError("PreAuthorize value could not be read", ex);
                    }
                })
                .orElseThrow(() -> new AssertionError("PreAuthorize annotation not found"));
    }

    private Map<String, Object> chunkMetadata(String chunkId) {
        return chunkMetadata(chunkId, "chunk-1", "chunk-3", 1);
    }

    private Map<String, Object> chunkMetadata(String chunkId, String previousChunkId, String nextChunkId, int order) {
        return Map.ofEntries(
                Map.entry(ChunkMetadata.KEY_OBJECT_TYPE, "attachment"),
                Map.entry(ChunkMetadata.KEY_OBJECT_ID, "123"),
                Map.entry(RagContextBuilder.KEY_CHUNK_ID, chunkId),
                Map.entry(ChunkMetadata.KEY_CHUNK_ORDER, order),
                Map.entry(ChunkMetadata.KEY_PREVIOUS_CHUNK_ID, previousChunkId == null ? "" : previousChunkId),
                Map.entry(ChunkMetadata.KEY_NEXT_CHUNK_ID, nextChunkId == null ? "" : nextChunkId));
    }
}
