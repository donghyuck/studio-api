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
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

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
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.web.cache.CaffeineRagAnswerCache;
import studio.one.platform.ai.web.cache.RagAnswerCache;
import studio.one.platform.ai.web.cache.RagAnswerCacheKey;
import studio.one.platform.ai.web.cache.RagCachedAnswer;
import studio.one.platform.ai.web.dto.ChatMemoryOptionsDto;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto;
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
        controller = new ChatController(providerRegistry, ragPipelineService,
                JsonMapper.builder().build());
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
        ChatResponseDto response = controller.chat(new ChatRequestDto(
                null,
                null,
                List.of(new ChatMessageDto("user", "hello")),
                null,
                null,
                null,
                null,
                null,
                null)).getBody().getData();

        verify(providerRegistry).chatPort(null);
        verify(defaultChatPort).chat(any(ChatRequest.class));
        assertThat(response.answer()).isEqualTo("default");
        assertThat(response.content()).isEqualTo("default");
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
        String streamExpression = preAuthorizeValue(ChatController.class.getMethod(
                "streamWithRag",
                ChatRagRequestDto.class,
                java.security.Principal.class));

        assertThat(expression).contains("services:ai_chat','write");
        assertThat(expression).contains("services:ai_rag','read");
        assertThat(expression).contains("@ragObjectAuthorizationRouter.canRead(#request)");
        assertThat(streamExpression).isEqualTo(expression);
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
    void ragStreamWithholdsDraftAndWritesCanonicalRagMetadata() throws Exception {
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult(
                        "doc-1",
                        "file text",
                        Map.of("sourceFileName", "sample.pdf", RagContextBuilder.KEY_CHUNK_ID, "chunk-1"),
                        0.9d)));
        when(defaultChatPort.stream(any(ChatRequest.class))).thenReturn(Stream.of(
                ChatStreamEvent.delta("요약", "model", ChatResponseMetadata.empty()),
                ChatStreamEvent.delta(" 답변", "model", ChatResponseMetadata.empty()),
                ChatStreamEvent.usage(ChatResponseMetadata.empty()),
                ChatStreamEvent.complete("model", ChatResponseMetadata.empty())));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        controller.streamWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
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
                "123"), null).getBody().writeTo(output);

        String body = output.toString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("event: rag_status")
                .contains("\"stage\":\"retrieval_started\"")
                .contains("\"stage\":\"retrieval_complete\"")
                .doesNotContain("event: delta", "\"delta\":\"요약\"", "\"delta\":\" 답변\"")
                .doesNotContain("event: usage")
                .contains("event: complete")
                .contains("\"ragReferences\"")
                .contains("\"sourceName\":\"sample.pdf\"")
                .contains("\"ragTiming\"")
                .contains("\"retrievalMs\"")
                .contains("\"generationMs\"")
                .contains("\"totalMs\"")
                .contains("\"canonicalContent\":\"관련 근거는 찾았지만 생성 답변의 인용 검증에 실패했습니다. 아래 검색된 근거 후보를 확인해 주세요.\"")
                .contains("\"citationValidationStatus\":\"MISSING_CITATION\"")
                .contains("\"requestId\"");
        verify(defaultChatPort).stream(any(ChatRequest.class));
        verify(defaultChatPort, times(0)).chat(any(ChatRequest.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragSyncAndSseCompleteShareCanonicalContentAndPackedReferences() throws Exception {
        RagSearchResult evidence = new RagSearchResult(
                "doc-1",
                "exact indexed excerpt",
                Map.of(
                        "revisionId", "revision-1",
                        "chunkId", "chunk-1",
                        "sourceRef", "page-7"),
                0.9d);
        when(ragPipelineService.search(any(RagSearchRequest.class))).thenReturn(List.of(evidence));
        when(defaultChatPort.chat(any(ChatRequest.class))).thenReturn(response("grounded answer [1]"));
        when(defaultChatPort.stream(any(ChatRequest.class))).thenReturn(Stream.of(
                ChatStreamEvent.delta("grounded answer [1]", "model", ChatResponseMetadata.empty()),
                ChatStreamEvent.complete("model", ChatResponseMetadata.empty())));
        ChatRagRequestDto request = new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "question")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "question",
                3,
                "attachment",
                "11");

        ChatResponseDto sync = controller.chatWithRag(request).getBody().getData();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        controller.streamWithRag(request, null).getBody().writeTo(output);
        String sse = output.toString(StandardCharsets.UTF_8);
        List<Map<String, Object>> references =
                (List<Map<String, Object>>) sync.metadata().get("ragReferences");
        String evidenceId = references.get(0).get("evidenceId").toString();

        assertThat(sync.content()).isEqualTo("grounded answer [1]");
        assertThat(sync.metadata())
                .containsEntry("canonicalContent", "grounded answer [1]")
                .containsEntry("citationValidationStatus", "INDEX_VALID");
        assertThat(references).singleElement().satisfies(reference -> assertThat(reference)
                .containsEntry("usageStatus", "CITED")
                .containsEntry("exactText", "exact indexed excerpt")
                .doesNotContainKeys("revisionId", "chunkId", "sourceRef"));
        assertThat(sse)
                .doesNotContain("event: delta")
                .contains("event: complete")
                .contains("\"canonicalContent\":\"grounded answer [1]\"")
                .contains("\"citationValidationStatus\":\"INDEX_VALID\"")
                .contains("\"evidenceId\":\"" + evidenceId + "\"")
                .contains("\"exactText\":\"exact indexed excerpt\"");
    }

    @Test
    void ragStreamSkipsLlmWhenRetrievalHasNoResults() throws Exception {
        when(ragPipelineService.search(any(RagSearchRequest.class))).thenReturn(List.of());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        controller.streamWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "missing")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "missing",
                3,
                "attachment",
                "123"), null).getBody().writeTo(output);

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("event: rag_status")
                .doesNotContain("event: delta")
                .contains("event: complete")
                .contains("\"ragSkippedChat\":true")
                .contains("\"ragSkipReason\":\"NO_RAG_RESULTS\"")
                .contains("\"canonicalContent\":\"검색 기준을 통과한 문서 구간이 없습니다.\"")
                .contains("\"reasonCode\":\"NO_RETRIEVAL_RESULTS\"")
                .contains("\"citationValidationStatus\":\"NO_PACKED_EVIDENCE\"");
        verify(defaultChatPort, times(0)).stream(any(ChatRequest.class));
        verify(defaultChatPort, times(0)).chat(any(ChatRequest.class));
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
                false, memoryStore, true,
                JsonMapper.builder().build());
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
        when(defaultChatPort.chat(any(ChatRequest.class))).thenReturn(response("default [1]"));
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
                "123",
                null,
                null,
                null,
                null,
                null,
                null,
                "default",
                null));
        controller.chat(memoryChat("chat-1", "follow up"));

        verify(defaultChatPort, times(2)).chat(captor.capture());
        List<studio.one.platform.ai.core.chat.ChatMessage> secondMessages = captor.getAllValues().get(1).messages();
        assertThat(secondMessages)
                .extracting(message -> message.role().name() + ":" + message.content())
                .containsExactly("USER:summarize", "ASSISTANT:default [1]", "USER:follow up");
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
                "123",
                null,
                null,
                null,
                null,
                null,
                null,
                "default",
                null));

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
    @SuppressWarnings("unchecked")
    void ragChatReturnsReferencesForPromptContext() {
        when(defaultChatPort.chat(any(ChatRequest.class))).thenReturn(response("default [1]"));
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult(
                        "doc-1",
                        "file text",
                        Map.of(
                                "sourceName", "sample.pdf",
                                "sourceFileName", "original-sample.pdf",
                                "docTitle", "Sample Document",
                                RagContextBuilder.KEY_CHUNK_ID, "chunk-1",
                                ChunkMetadata.KEY_CHUNK_ORDER, 7,
                                "page", 3,
                                "sourceRef", "page[3]"),
                        0.9d)));

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
                "123")).getBody().getData();

        List<Map<String, Object>> references = (List<Map<String, Object>>) response.metadata().get("ragReferences");
        assertThat(references).hasSize(1);
        assertThat(response.answer()).isEqualTo("default [1]");
        assertThat(response.content()).isEqualTo("default [1]");
        assertThat(references.get(0))
                .containsEntry("citationIndex", 1)
                .containsEntry("usageStatus", "CITED")
                .containsEntry("sourceName", "original-sample.pdf")
                .containsEntry("title", "Sample Document")
                .containsEntry("score", 0.9d)
                .containsEntry("exactText", "file text")
                .containsEntry("page", 3)
                .containsEntry("locator", "페이지 3");
        assertThat(references.get(0)).doesNotContainKeys(
                "content", "metadata", "documentId", "chunkId", "sourceRef");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragChatExcludesBoilerplateFromPromptAndReferences() {
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(
                        new RagSearchResult(
                                "doc-copyright",
                                "ISBN 978-0-00-000000-0",
                                Map.of("section", "판권", "chunkId", "chunk-copyright"),
                                0.95d),
                        new RagSearchResult(
                                "doc-body",
                                "The actual argument from the chapter.",
                                Map.of("section", "첫 번째 장", "chunkId", "chunk-body"),
                                0.85d),
                        new RagSearchResult(
                                "doc-image-copyright",
                                "Image credits",
                                Map.of("section", "이미지 저작권", "chunkId", "chunk-image-copyright"),
                                0.80d)));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "첫 번째 장에서 저자는 무엇을 주장하는가")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "첫 번째 장에서 저자는 무엇을 주장하는가",
                5,
                "attachment",
                "123")).getBody().getData();

        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("The actual argument from the chapter.")
                .doesNotContain("ISBN 978-0-00-000000-0", "Image credits");
        List<Map<String, Object>> references = (List<Map<String, Object>>) response.metadata().get("ragReferences");
        assertThat(references).singleElement()
                .satisfies(reference -> assertThat(reference)
                        .containsEntry("usageStatus", "RETRIEVED_ONLY")
                        .containsEntry("exactText", "The actual argument from the chapter.")
                        .doesNotContainKey("documentId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragChatSkipsLlmWhenRetrievalHasNoFinalResults() {
        RagRetrievalDiagnostics diagnostics = new RagRetrievalDiagnostics(
                RagRetrievalDiagnostics.Strategy.HYBRID,
                2,
                0,
                0.6d,
                0.7d,
                0.3d,
                null,
                null,
                2,
                2,
                0.6d,
                2,
                0);
        when(ragPipelineService.search(any(RagSearchRequest.class))).thenReturn(List.of());
        when(ragPipelineService.latestDiagnostics()).thenReturn(Optional.of(diagnostics));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        "google",
                        null,
                        List.of(new ChatMessageDto("user", "여름 휴가 규정이 있는가")),
                        "gemini-2.5-flash",
                        null,
                        null,
                        null,
                        null,
                        null),
                "여름 휴가 규정이 있는가",
                2,
                null,
                null,
                null,
                null,
                null,
                2,
                0.6d,
                false)).getBody().getData();

        assertThat(response.messages())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly("assistant:검색 기준을 통과한 문서 구간이 없습니다.");
        assertThat(response.model()).isEqualTo("gemini-2.5-flash");
        assertThat(response.metadata())
                .containsEntry("ragSkippedChat", true)
                .containsEntry("ragSkipReason", "NO_RAG_RESULTS")
                .containsEntry("canonicalContent", "검색 기준을 통과한 문서 구간이 없습니다.")
                .containsEntry("citationValidationStatus", "NO_PACKED_EVIDENCE")
                .containsEntry("ragReferences", List.of());
        Map<String, Object> summary = (Map<String, Object>) response.metadata().get("ragRetrievalSummary");
        assertThat(summary)
                .containsEntry("initialResultCount", 2)
                .containsEntry("finalResultCount", 0)
                .containsEntry("effectiveMinScore", 0.6d)
                .containsEntry("beforeMinScoreCount", 2)
                .containsEntry("afterMinScoreCount", 0);
        assertThat(response.metadata()).doesNotContainKey("ragDiagnostics");
        verifyNoInteractions(providerRegistry, defaultChatPort, googleChatPort);
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
                "6",
                null,
                null,
                null,
                null,
                null,
                null,
                "default",
                null));

        verify(ragPipelineService).search(ragCaptor.capture());
        assertThat(ragCaptor.getValue().metadataFilter().objectType()).isEqualTo("2001");
        assertThat(ragCaptor.getValue().metadataFilter().objectId()).isEqualTo("6");
    }

    @Test
    void ragExactCacheSkipsSecondProviderCallAfterCurrentEvidenceMatches() {
        when(defaultChatPort.chat(any(ChatRequest.class))).thenReturn(response("supported answer [1]"));
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult(
                        "doc-1",
                        "file text",
                        Map.of("revisionId", "rev-1", "sourceRef", "page-1"),
                        0.9d)));
        controller = new ChatController(
                providerRegistry,
                ragPipelineService,
                new RagChatRetrievalService(ragPipelineService),
                RagContextBuilder.defaults(),
                false,
                null,
                false,
                null,
                JsonMapper.builder().build(),
                4,
                100,
                RagPipelineOptions.defaults(),
                null,
                null,
                AiModelUsageStore.noop(),
                new CaffeineRagAnswerCache(Duration.ofMinutes(15)));
        ChatRagRequestDto request = new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "question")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "question",
                3,
                "attachment",
                "11");
        Principal principal = () -> "cache-test-user";

        ChatResponseDto first = controller.chatWithRag(request, principal).getBody().getData();
        ChatResponseDto second = controller.chatWithRag(request, principal).getBody().getData();
        ByteArrayOutputStream streamOutput = new ByteArrayOutputStream();
        try {
            controller.streamWithRag(request, principal).getBody().writeTo(streamOutput);
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
        String stream = streamOutput.toString(StandardCharsets.UTF_8);

        assertThat(first.content()).isEqualTo("supported answer [1]");
        assertThat(second.content()).isEqualTo(first.content());
        assertThat(second.metadata()).containsEntry("ragAnswerCache", "HIT");
        assertThat(stream)
                .doesNotContain("event: delta")
                .contains("event: complete", "\"ragAnswerCache\":\"HIT\"");
        assertThat(stream).doesNotContain("event: usage");
        verify(defaultChatPort, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void ragExactCacheRejectsStructurallyInvalidCanonicalPayload() {
        RagSearchResult evidence = new RagSearchResult(
                "doc-1",
                "file text",
                Map.of("revisionId", "rev-1", "sourceRef", "page-1"),
                0.9d);
        when(ragPipelineService.search(any(RagSearchRequest.class))).thenReturn(List.of(evidence));
        when(defaultChatPort.chat(any(ChatRequest.class))).thenReturn(response("supported answer [1]"));
        String fingerprint = RagContextBuilder.defaults()
                .buildWithDiagnostics(List.of(evidence), List.of(evidence))
                .evidenceSet()
                .contextFingerprint();
        Instant createdAt = Instant.now();
        RagCachedAnswer invalidAnswer = new RagCachedAnswer(
                "corrupt answer [999]",
                "cached-model",
                "INDEX_VALID",
                fingerprint,
                createdAt,
                createdAt.plus(Duration.ofDays(1)));
        RagAnswerCache invalidCache = new RagAnswerCache() {
            @Override
            public Optional<RagCachedAnswer> get(RagAnswerCacheKey key) {
                return Optional.of(invalidAnswer);
            }

            @Override
            public void put(RagAnswerCacheKey key, RagCachedAnswer answer) {
            }

            @Override
            public Duration ttl() {
                return Duration.ofMinutes(5);
            }
        };
        controller = new ChatController(
                providerRegistry,
                ragPipelineService,
                new RagChatRetrievalService(ragPipelineService),
                RagContextBuilder.defaults(),
                false,
                null,
                false,
                null,
                JsonMapper.builder().build(),
                4,
                100,
                RagPipelineOptions.defaults(),
                null,
                null,
                AiModelUsageStore.noop(),
                invalidCache);
        ChatRagRequestDto request = new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "question")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "question",
                3,
                "attachment",
                "11");

        ChatResponseDto response = controller.chatWithRag(request, () -> "cache-test-user")
                .getBody()
                .getData();

        assertThat(response.content()).isEqualTo("supported answer [1]");
        assertThat(response.metadata()).containsEntry("ragAnswerCache", "MISS");
        verify(defaultChatPort).chat(any(ChatRequest.class));
    }

    @Test
    void ragExactCacheDoesNotShareAcrossPrincipals() {
        when(defaultChatPort.chat(any(ChatRequest.class))).thenReturn(response("supported answer [1]"));
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "file text", Map.of(), 0.9d)));
        controller = new ChatController(
                providerRegistry,
                ragPipelineService,
                new RagChatRetrievalService(ragPipelineService),
                RagContextBuilder.defaults(),
                false,
                null,
                false,
                null,
                JsonMapper.builder().build(),
                4,
                100,
                RagPipelineOptions.defaults(),
                null,
                null,
                AiModelUsageStore.noop(),
                new CaffeineRagAnswerCache(Duration.ofMinutes(15)));
        ChatRagRequestDto request = new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "question")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "question",
                3,
                "attachment",
                "11");

        controller.chatWithRag(request, () -> "user-a");
        controller.chatWithRag(request, () -> "user-b");

        verify(defaultChatPort, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void ragChatPrefersTopKOverLegacyRagTopKAndPassesMinScore() {
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
                2,
                "2001",
                "6",
                null,
                null,
                null,
                5,
                0.7d,
                null,
                "default",
                null));

        verify(ragPipelineService).search(ragCaptor.capture());
        assertThat(ragCaptor.getValue().topK()).isEqualTo(5);
        assertThat(ragCaptor.getValue().requestedTopK()).isEqualTo(5);
        assertThat(ragCaptor.getValue().minScore()).isEqualTo(0.7d);
        assertThat(ragCaptor.getValue().requestedMinScore()).isEqualTo(0.7d);
    }

    @Test
    void ragChatRequestDeserializesRetrievalStrategyOptions() throws Exception {
        tools.jackson.databind.ObjectMapper mapper = JsonMapper.builder().build();

        ChatRagRequestDto request = mapper.readValue("""
                {
                  "chat": {
                    "messages": [{"role": "user", "content": "question"}]
                  },
                  "ragQuery": "question",
                  "topK": 3,
                  "retrievalStrategy": "hybrid",
                  "retrievalOptions": {
                    "structureTopK": 4,
                    "ideaBlockTopK": 5,
                    "finalTopK": 6,
                    "minScore": 0.7,
                    "dedupe": true,
                    "includeDebugChunks": true
                  }
                }
                """, ChatRagRequestDto.class);

        assertThat(request.retrievalStrategy()).isEqualTo("hybrid");
        assertThat(request.retrievalOptions().structureTopK()).isEqualTo(4);
        assertThat(request.retrievalOptions().ideaBlockTopK()).isEqualTo(5);
        assertThat(request.retrievalOptions().finalTopK()).isEqualTo(6);
        assertThat(request.retrievalOptions().minScore()).isEqualTo(0.7d);
        assertThat(request.retrievalOptions().dedupe()).isTrue();
        assertThat(request.retrievalOptions().includeDebugChunks()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragChatAppliesObjectRetrievalPolicyWhenStrategyIsOmitted() {
        InMemoryRagRetrievalPolicyStore policyStore = new InMemoryRagRetrievalPolicyStore();
        InMemoryRagRetrievalPolicyUsageStore usageStore = new InMemoryRagRetrievalPolicyUsageStore();
        policyStore.save(new studio.one.platform.ai.web.dto.RagRetrievalPolicyDto(
                "attachment",
                "123",
                "hybrid",
                new ChatRagRetrievalOptionsDto(2, 3, 4, 0.5d, true, false, null, null),
                "reqs-1",
                "reval-1",
                1.0d,
                1.0d,
                1.0d,
                10.0d,
                java.time.Instant.now(),
                java.time.Instant.now()));
        controller = new ChatController(
                providerRegistry,
                ragPipelineService,
                new RagChatRetrievalService(ragPipelineService),
                RagContextBuilder.defaults(),
                false,
                null,
                false,
                null,
                JsonMapper.builder().build(),
                4,
                100,
                studio.one.platform.ai.service.pipeline.RagPipelineOptions.defaults(),
                policyStore,
                usageStore);
        ArgumentCaptor<RagSearchRequest> ragCaptor = ArgumentCaptor.forClass(RagSearchRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("structure", "structure text",
                        Map.of(RagContextBuilder.KEY_CHUNK_ID, "chunk-1", "strategy", "structure-based"), 0.8d)))
                .thenReturn(List.of(new RagSearchResult("blockify", "blockify text",
                        Map.of(RagContextBuilder.KEY_CHUNK_ID, "chunk-2", "actualChunkingStrategy", "blockify"), 0.9d)))
                .thenReturn(List.of());

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "question")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "question",
                5,
                "attachment",
                "123")).getBody().getData();

        verify(ragPipelineService, times(3)).search(ragCaptor.capture());
        List<RagSearchRequest> searches = ragCaptor.getAllValues();
        assertThat(searches).extracting(RagSearchRequest::topK).containsExactly(2, 3, 3);
        assertThat(searches.get(0).metadataFilter().equalsCriteria())
                .containsEntry(ChunkMetadata.KEY_STRATEGY, "structure-based");
        assertThat(searches.get(1).metadataFilter().equalsCriteria())
                .containsEntry("actualChunkingStrategy", "blockify");
        assertThat(searches.get(2).metadataFilter().equalsCriteria())
                .containsEntry(ChunkMetadata.KEY_CHUNK_TYPE, "ideaBlock");
        Map<String, Object> policyMetadata = (Map<String, Object>) response.metadata().get("retrievalPolicy");
        assertThat(policyMetadata)
                .containsEntry("applied", true)
                .containsEntry("objectType", "attachment")
                .containsEntry("retrievalStrategy", "hybrid")
                .containsEntry("questionSetId", "reqs-1")
                .containsEntry("evaluationRunId", "reval-1");
        assertThat(policyMetadata).doesNotContainKey("objectId");
        assertThat(usageStore.list("attachment", "123")).singleElement()
                .satisfies(usage -> {
                    assertThat(usage.retrievalStrategy()).isEqualTo("hybrid");
                    assertThat(usage.resultCount()).isEqualTo(2);
                    assertThat(usage.skippedChat()).isFalse();
                    assertThat(usage.topK()).isEqualTo(5);
                    assertThat(usage.minScore()).isEqualTo(0.5d);
                });
        assertThat(usageStore.summary("attachment", "123").usageCount()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragChatReturnsRetrievalDebugMetadataWhenEnabled() {
        controller = new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), true,
                JsonMapper.builder().build());
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("structure", "structure text",
                        Map.of(RagContextBuilder.KEY_CHUNK_ID, "chunk-1", "strategy", "structure-based"), 0.8d)))
                .thenReturn(List.of(new RagSearchResult("blockify", "blockify text",
                        Map.of(RagContextBuilder.KEY_CHUNK_ID, "chunk-2", "actualChunkingStrategy", "blockify"), 0.9d)))
                .thenReturn(List.of());

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "question")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "question",
                5,
                "attachment",
                "1",
                null,
                null,
                null,
                5,
                null,
                true,
                "hybrid",
                new studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto(3, 3, 2, null, true, true, null, null)))
                .getBody().getData();

        Map<String, Object> retrieval = (Map<String, Object>) response.metadata().get("retrieval");
        assertThat(retrieval)
                .containsEntry("requestedStrategy", "hybrid")
                .containsEntry("resolvedStrategy", "hybrid")
                .containsEntry("finalCount", 2);
        assertThat((List<?>) retrieval.get("legs")).hasSize(3);
        assertThat((List<?>) retrieval.get("chunks")).hasSize(2);
    }

    @Test
    void ragChatUsesObjectScopedCandidatesForContextExpansion() {
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, TestWindowChunkContextExpander.asList()),
                JsonMapper.builder().build());
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
                .contains("previous\nseed\nnext");
    }

    @Test
    void ragChatUsesObjectChunksForKoreanPlotSummaryQuestion() {
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.listByObject("attachment", "3", 20))
                .thenReturn(List.of(
                        new RagSearchResult("chunk-1", "first plot fragment", chunkMetadata("chunk-1"), 1.0d),
                        new RagSearchResult("chunk-2", "second plot fragment", chunkMetadata("chunk-2"), 1.0d)));

        controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "줄거리를 요약해줘")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "줄거리를 요약해줘",
                5,
                "attachment",
                "3"));

        verify(ragPipelineService).listByObject("attachment", "3", 20);
        verify(ragPipelineService, times(0)).search(any(RagSearchRequest.class));
        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("first plot fragment")
                .contains("second plot fragment")
                .contains("각 실질 문단과 목록 항목의 같은 줄 끝에는 이를 뒷받침하는 근거 번호")
                .contains("요양 또는 치료 시설을 근거 없이 병원으로 바꾸지 마세요")
                .contains("영화, 책, 이야기를 원본 전체의 줄거리로 오인하지 마세요");
    }

    @Test
    void ragChatDetectsSummaryIntentFromTheLastUserMessageWithoutDuplicatedRagQuery() {
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.listByObject("attachment", "3", 12))
                .thenReturn(List.of(
                        new RagSearchResult("chunk-1", "beginning", chunkMetadata("chunk-1"), 1.0d),
                        new RagSearchResult("chunk-2", "ending", chunkMetadata("chunk-2"), 1.0d)));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "줄거리를 요약해줘")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                null,
                "attachment",
                "3")).getBody().getData();

        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("beginning", "ending", "원본 문서 전체의 요약 또는 줄거리");
        assertThat(response.metadata())
                .containsEntry("ragQueryIntent", "DOCUMENT_SUMMARY")
                .containsEntry("ragRetrievalMode", "WHOLE_DOCUMENT_CONTEXT")
                .containsEntry("overviewCoverageStatus", "FULL");
    }

    @Test
    void ragChatReducesLargeWholeDocumentContextBeforeTheFinalAnswer() {
        String content = "beginning\n" + "a".repeat(40_500) + "\nending\n" + "b".repeat(40_500);
        when(ragPipelineService.listByObject("attachment", "3", 12))
                .thenReturn(List.of(new RagSearchResult(
                        "chunk-1",
                        content,
                        Map.of(
                                "chunkId", "chunk-1",
                                "chunkOrder", 0,
                                "startOffset", 0,
                                "endOffset", content.length()),
                        1.0d)));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "줄거리를 요약해줘")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                null,
                "attachment",
                "3")).getBody().getData();

        verify(defaultChatPort, times(4)).chat(any(ChatRequest.class));
        assertThat(response.metadata())
                .containsEntry("overviewReduction", "MAP_REDUCE")
                .containsEntry("overviewReductionSegmentCount", 3)
                .containsEntry("overviewReductionCacheHit", false);
        assertThat((Map<String, Object>) response.metadata().get("ragTiming"))
                .containsKeys("retrievalMs", "overviewReductionMs", "generationMs", "totalMs");
    }

    @Test
    void ragChatUsesPreExtractedKeyPointsWithoutSemanticSearch() {
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.listByObject("attachment", "6", 20))
                .thenReturn(List.of(new RagSearchResult(
                        "chunk-1",
                        "raw document body",
                        Map.of("keyPoints", List.of("다항식의 연산", "인수분해의 기초")),
                        1.0d)));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "이 문서의 핵심 내용을 요약해줘")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "이 문서의 핵심 내용을 요약해줘",
                5,
                "attachment",
                "6")).getBody().getData();

        verify(ragPipelineService, times(0)).search(any(RagSearchRequest.class));
        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("다항식의 연산", "인수분해의 기초")
                .doesNotContain("raw document body");
        assertThat(response.metadata())
                .containsEntry("ragQueryIntent", "KEY_POINTS")
                .containsEntry("ragRetrievalMode", "METADATA_OVERVIEW");
    }

    @Test
    void ragChatUsesBroaderEvidenceSearchAndServerPromptForInterpretiveQuestions() {
        ArgumentCaptor<RagSearchRequest> ragCaptor = ArgumentCaptor.forClass(RagSearchRequest.class);
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.listByObject("attachment", "3", 32))
                .thenReturn(List.of(new RagSearchResult(
                        "chunk-1",
                        "홀든은 타인의 위선을 비판하면서도 동생 피비를 보호하려 한다.",
                        chunkMetadata("chunk-1"),
                        1.0d)));
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult(
                        "chunk-1",
                        "홀든은 타인의 위선을 비판하면서도 동생 피비를 보호하려 한다.",
                        chunkMetadata("chunk-1"),
                        0.67d)));

        ChatResponseDto response = controller.chatWithRag(new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        "문서에 명시된 내용만 답변하세요.",
                        List.of(new ChatMessageDto("user", "주인공의 MBTI 성격 유형을 문서 근거로 추정해줘")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "주인공의 MBTI 성격 유형을 문서 근거로 추정해줘",
                2,
                "attachment",
                "3",
                null,
                null,
                null,
                2,
                0.7d,
                null,
                null,
                new ChatRagRetrievalOptionsDto(2, 2, 2, 0.7d, true, false, null, true)))
                .getBody().getData();

        verify(ragPipelineService).search(ragCaptor.capture());
        assertThat(ragCaptor.getValue().topK()).isEqualTo(8);
        assertThat(ragCaptor.getValue().requestedTopK()).isEqualTo(8);
        assertThat(ragCaptor.getValue().minScore()).isEqualTo(0.55d);
        assertThat(ragCaptor.getValue().requestedMinScore()).isEqualTo(0.55d);
        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("문서에 명시된 내용만 답변하세요.")
                .contains("문서에 분류명이 없다는 이유만으로 답변을 거부하지 말고")
                .contains("정확히 한 문단")
                .contains("'문서 사실:'과 '해석:'")
                .contains("확신도");
        assertThat(response.metadata())
                .containsEntry("ragQueryIntent", "INTERPRETIVE_ANALYSIS")
                .containsEntry("ragRetrievalMode", "SEMANTIC_SEARCH")
                .containsEntry("ragRetrievalTopK", 8)
                .containsEntry("ragRetrievalMinScore", 0.55d)
                .containsEntry("answerType", "EVIDENCE_BASED_INFERENCE")
                .containsEntry("ragInferenceEnabled", true);
    }

    @Test
    void ragChatUsesNonAttachmentObjectScopedCandidatesForContextExpansion() {
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, TestWindowChunkContextExpander.asList()),
                JsonMapper.builder().build());
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
                JsonMapper.builder().build(),
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
                JsonMapper.builder().build(),
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
                JsonMapper.builder().build(),
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
                100,
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
                JsonMapper.builder().build(),
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
        controller = new ChatController(providerRegistry, ragPipelineService, new RagContextBuilder(2, 12_000, true),
                JsonMapper.builder().build());
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
        assertThat(context).contains("first", "second");
        assertThat(context).doesNotContain("third");
    }

    @Test
    void ragChatLimitsContextCharacters() {
        controller = new ChatController(providerRegistry, ragPipelineService, new RagContextBuilder(8, 80, true),
                JsonMapper.builder().build());
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
                .doesNotContain("[truncated]")
                .contains("01234567890123456789")
                .doesNotContain("0123456789".repeat(20));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragChatUsesPackedPreviewForPromptAndReferences() {
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(
                        8,
                        12_000,
                        24,
                        true,
                        new AiWebRagProperties.ExpansionProperties(),
                        List.of()),
                true,
                JsonMapper.builder().build());
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        String rawContent = "A".repeat(80) + "B".repeat(80);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult(
                        "doc-1",
                        rawContent,
                        Map.of(
                                "sourceName", "large.pdf",
                                RagContextBuilder.KEY_CHUNK_ID, "chunk-1",
                                ChunkMetadata.KEY_OBJECT_TYPE, "attachment",
                                ChunkMetadata.KEY_OBJECT_ID, "123"),
                        0.9d)));

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

        verify(defaultChatPort).chat(chatCaptor.capture());
        String promptContext = chatCaptor.getValue().messages().get(0).content();
        assertThat(promptContext)
                .doesNotContain("[truncated]")
                .contains(rawContent.substring(0, 24))
                .doesNotContain(rawContent)
                .doesNotContain("doc-1", "chunk-1", "attachment", "123", "score=");

        List<Map<String, Object>> references = (List<Map<String, Object>>) response.metadata().get("ragReferences");
        assertThat(references).hasSize(1);
        assertThat(references.get(0))
                .containsEntry("sourceName", "large.pdf")
                .containsEntry("usageStatus", "RETRIEVED_ONLY")
                .doesNotContainKeys("documentId", "chunkId");
        assertThat((String) references.get(0).get("exactText"))
                .isEqualTo(rawContent.substring(0, 24))
                .doesNotContain(rawContent);

        Map<String, Object> contextDiagnostics = (Map<String, Object>) response.metadata()
                .get("ragContextDiagnostics");
        assertThat(contextDiagnostics)
                .containsEntry("includedCount", 1)
                .containsEntry("compressedHitCount", 1)
                .containsEntry("skippedHitCount", 0)
                .containsEntry("maxChars", 12_000)
                .doesNotContainKeys("content", "snippet", "text", "chunk");
        assertThat(contextDiagnostics.values()).doesNotContain(rawContent);
    }

    @Test
    void ragChatCanOmitScoresFromContext() {
        controller = new ChatController(providerRegistry, ragPipelineService, new RagContextBuilder(8, 12_000, false),
                JsonMapper.builder().build());
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
                .doesNotContain("score=");
    }

    @Test
    void ragChatDoesNotExposeDiagnosticsWhenClientDebugIsDisabled() {
        controller = new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), true,
                JsonMapper.builder().build());
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
        assertThat(response.metadata()).containsKey("ragRetrievalSummary");
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
        controller = new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), true,
                JsonMapper.builder().build());
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
                .containsEntry("effectiveTopK", 3)
                .containsEntry("effectiveMinScore", 0.15d)
                .containsEntry("beforeMinScoreCount", 1)
                .containsEntry("afterMinScoreCount", 1)
                .doesNotContainKeys("content", "snippet", "text", "chunk");
        assertThat(ragDiagnostics.values()).doesNotContain("sensitive file body");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ragChatExposesSafeContextExpansionDiagnosticsWhenDebugIsAllowed() {
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 12_000, true, TestWindowChunkContextExpander.asList()), true,
                JsonMapper.builder().build());
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
    @SuppressWarnings("unchecked")
    void ragChatFallsBackToSearchChunkWhenExpandedContextExceedsLimit() {
        controller = new ChatController(providerRegistry, ragPipelineService,
                new RagContextBuilder(8, 80, true, TestWindowChunkContextExpander.asList()), true,
                JsonMapper.builder().build());
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("chunk-2", "seed body",
                        chunkMetadata("chunk-2"), 0.9d)));
        when(ragPipelineService.listByObject("attachment", "123", 12))
                .thenReturn(List.of(
                        new RagSearchResult("chunk-1", "previous text that makes the expanded content too long",
                                chunkMetadata("chunk-1", null, "chunk-2", 0), 1.0d),
                        new RagSearchResult("chunk-2", "seed body",
                                chunkMetadata("chunk-2", "chunk-1", "chunk-3", 1), 1.0d),
                        new RagSearchResult("chunk-3", "next text that makes the expanded content too long",
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

        verify(defaultChatPort).chat(chatCaptor.capture());
        assertThat(chatCaptor.getValue().messages().get(0).content())
                .contains("seed body")
                .doesNotContain("참고할 문서가 없습니다")
                .doesNotContain("previous text that makes the expanded content too long")
                .doesNotContain("next text that makes the expanded content too long");
        Map<String, Object> contextDiagnostics = (Map<String, Object>) response.metadata()
                .get("ragContextDiagnostics");
        assertThat(contextDiagnostics)
                .containsEntry("applied", false)
                .containsEntry("fallbackReason", "context_limit")
                .containsEntry("fallbackHitCount", 1);
        List<Map<String, Object>> references = (List<Map<String, Object>>) response.metadata().get("ragReferences");
        assertThat(references).hasSize(1);
        assertThat(references.get(0))
                .containsEntry("exactText", "seed body")
                .containsEntry("usageStatus", "RETRIEVED_ONLY")
                .doesNotContainKey("chunkId");
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
                memoryStore(), true,
                JsonMapper.builder().build());
    }

    private ChatController conversationController() {
        return new ChatController(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), false,
                memoryStore(), true, new ConversationChatService(new InMemoryConversationRepository()),
                JsonMapper.builder().build());
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
