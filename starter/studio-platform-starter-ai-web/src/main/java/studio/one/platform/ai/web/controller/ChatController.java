/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ChatController.java
 *      @date 2025
 *
 */

package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.security.Principal;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Valid;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMemoryStore;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.chat.ChatResponseMetadata;
import studio.one.platform.ai.core.chat.ChatStreamEvent;
import studio.one.platform.ai.core.chat.ChatStreamEventType;
import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
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
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

/**
 * 채팅 요청을 받아 ChatPort로 위임 후 응답을 DTO로 반환합니다
 * 
 * REST controller exposing chat completions. The base path defaults to {@code /api/ai}
 * and can be overridden with {@code studio.ai.endpoints.base-path}. Requests are
 * delegated through {@link AiProviderRegistry} and wrapped with {@link ApiResponse}.
 */
@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/chat")
@Validated
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String OBJECT_TYPE_ATTACHMENT = "attachment";
    private static final int DEFAULT_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER = 4;
    private static final int DEFAULT_CONTEXT_EXPANSION_MAX_CANDIDATES = 100;
    private static final int MAX_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER = 20;
    private static final int MAX_CONTEXT_EXPANSION_CANDIDATES = 500;

    private final AiProviderRegistry providerRegistry;
    private final RagPipelineService ragPipelineService;
    private final RagContextBuilder ragContextBuilder;
    private final int ragContextCandidateMultiplier;
    private final int ragContextMaxCandidates;
    private final boolean allowClientDebug;
    private final ChatMemoryStore chatMemoryStore;
    private final boolean chatMemoryEnabled;
    private final ConversationChatService conversationChatService;
    private final ObjectMapper objectMapper;

    public ChatController(AiProviderRegistry providerRegistry, RagPipelineService ragPipelineService) {
        this(providerRegistry, ragPipelineService, RagContextBuilder.defaults());
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, false);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug, null, false);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug,
                chatMemoryStore, chatMemoryEnabled, null);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug,
                chatMemoryStore, chatMemoryEnabled, conversationChatService,
                Jackson2ObjectMapperBuilder.json().build());
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            int ragContextCandidateMultiplier,
            int ragContextMaxCandidates) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.ragPipelineService = Objects.requireNonNull(ragPipelineService, "ragPipelineService");
        this.ragContextBuilder = Objects.requireNonNull(ragContextBuilder, "ragContextBuilder");
        this.ragContextCandidateMultiplier = clamp(
                ragContextCandidateMultiplier,
                DEFAULT_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER,
                MAX_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER);
        this.ragContextMaxCandidates = clamp(
                ragContextMaxCandidates,
                DEFAULT_CONTEXT_EXPANSION_MAX_CANDIDATES,
                MAX_CONTEXT_EXPANSION_CANDIDATES);
        this.allowClientDebug = allowClientDebug;
        this.chatMemoryStore = chatMemoryStore;
        this.chatMemoryEnabled = chatMemoryEnabled;
        this.conversationChatService = conversationChatService;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            int ragContextCandidateMultiplier) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug, chatMemoryStore,
                chatMemoryEnabled, conversationChatService, objectMapper,
                ragContextCandidateMultiplier, DEFAULT_CONTEXT_EXPANSION_MAX_CANDIDATES);
    }

    /**
     * @deprecated Since 2.x. Pass scalar candidate settings to this controller and keep window/parent expansion
     * options on {@link RagContextBuilder}.
     */
    @Deprecated(since = "2.x", forRemoval = false)
    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            AiWebRagProperties.ExpansionProperties ragContextExpansion) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug, chatMemoryStore,
                chatMemoryEnabled, conversationChatService, objectMapper,
                ragContextExpansion == null
                        ? DEFAULT_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER
                        : ragContextExpansion.getCandidateMultiplier(),
                ragContextExpansion == null
                        ? DEFAULT_CONTEXT_EXPANSION_MAX_CANDIDATES
                        : ragContextExpansion.getMaxCandidates());
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug, chatMemoryStore,
                chatMemoryEnabled, conversationChatService, objectMapper,
                DEFAULT_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER);
    }

    /**
     * Chat completion endpoint under {@code ${studio.ai.endpoints.base-path:/api/ai}/chat}.
     * <p>Usage:
     * <pre>
     * POST /api/ai/chat
     * Authorization: Bearer &lt;token&gt;   (requires services:ai_chat write)
     * {
     *   "messages": [
     *     {"role": "user", "content": "Hello"}
     *   ],
     *   "model": "gpt-4o-mini",
     *   "temperature": 0.2,
     *   "topP": 0.9,
     *   "maxOutputTokens": 256,
     *   "stopSequences": ["STOP"]
     * }
     *
     * 200 OK
     * {
     *   "data": {
     *     "messages": [
     *       {"role":"assistant","content":"Hi there!"}
     *     ],
     *     "model": "gpt-4o-mini",
     *     "metadata": {"provider":"vertex"}
     *   }
     * }
     * </pre>
     * Send an ordered list of chat messages; optional tuning parameters are forwarded
     * to the configured {@link ChatPort} implementation.
     */
    @PostMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<ApiResponse<ChatResponseDto>> chat(
            @Valid @RequestBody ChatRequestDto request,
            Principal principal) {
        return chatInternal(request, principal);
    }

    ResponseEntity<ApiResponse<ChatResponseDto>> chat(ChatRequestDto request) {
        return chatInternal(request, null);
    }

    private ResponseEntity<ApiResponse<ChatResponseDto>> chatInternal(ChatRequestDto request, Principal principal) {
        ChatMemoryContext memory = resolveMemory(request, principal);
        List<ChatMessage> domainMessages = toDomainMessages(request, memory.history());
        ChatResponse response = chatPort(request.provider()).chat(toDomainChatRequest(request, domainMessages));
        int memoryMessageCount = appendMemory(memory, request.messages(), response);
        appendConversation(principal, memory, request.messages().stream().map(this::toDomainMessage).toList(), response);
        return ResponseEntity.ok(ApiResponse.ok(toDto(response, null, false, memoryMetadata(memory, memoryMessageCount))));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<StreamingResponseBody> stream(
            @Valid @RequestBody ChatRequestDto request,
            Principal principal) {
        ChatMemoryContext memory = resolveMemory(request, principal);
        List<ChatMessage> domainMessages = toDomainMessages(request, memory.history());
        ChatPort port = chatPort(request.provider());
        ChatRequest domainRequest = toDomainChatRequest(request, domainMessages);
        String requestId = UUID.randomUUID().toString();
        StreamingResponseBody body = outputStream -> writeStreamEvents(
                outputStream,
                requestId,
                port,
                domainRequest,
                memory,
                request.messages().stream().map(this::toDomainMessage).toList(),
                principal);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    /**
     * RAG 검색 결과를 시스템 프롬프트로 주입한 뒤 챗을 수행한다.
     */
    @PostMapping("/rag")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write') and "
            + "(#request.objectType() == null or !#request.objectType().trim().equalsIgnoreCase('attachment') "
            + "or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRag(
            @Valid @RequestBody ChatRagRequestDto request,
            Principal principal) {
        return chatWithRagInternal(request, principal);
    }

    ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRag(ChatRagRequestDto request) {
        return chatWithRagInternal(request, null);
    }

    private ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRagInternal(
            ChatRagRequestDto request,
            Principal principal) {
        ChatRequestDto chat = request.chat();
        int ragTopK = request.ragTopK() != null ? request.ragTopK() : 3;
        ObjectScope objectScope = resolveObjectScope(request.objectType(), request.objectId());
        String objectType = objectScope.objectType();
        String objectId = objectScope.objectId();

        List<RagSearchResult> ragResults;
        String ragQuery = request.ragQuery();
        boolean hasFilter = objectScope.hasFilter();

        if (ragQuery == null || ragQuery.isBlank()) {
            if (!hasFilter) {
                throw new IllegalArgumentException("ragQuery가 없으면 objectType 또는 objectId를 제공해야 합니다");
            }
            ragResults = ragPipelineService.listByObject(objectType, objectId, ragTopK);
        } else {
            String resolvedQuery = resolveRagQuery(request);
            if (hasFilter) {
                ragResults = ragPipelineService.search(new RagSearchRequest(
                        resolvedQuery,
                        ragTopK,
                        MetadataFilter.objectScope(objectType, objectId),
                        request.embeddingProfileId(),
                        request.embeddingProvider(),
                        request.embeddingModel()));
            } else {
                ragResults = ragPipelineService.search(new RagSearchRequest(
                        resolvedQuery,
                        ragTopK,
                        MetadataFilter.empty(),
                        request.embeddingProfileId(),
                        request.embeddingProvider(),
                        request.embeddingModel()));
            }
        }
        RagRetrievalDiagnostics diagnostics = ragPipelineService.latestDiagnostics().orElse(null);

        List<RagSearchResult> expansionCandidates = contextExpansionCandidates(
                ragResults,
                objectType,
                objectId,
                ragTopK,
                ragQuery == null || ragQuery.isBlank());
        RagContextBuilder.BuildResult contextResult = ragContextBuilder.buildWithDiagnostics(ragResults, expansionCandidates);
        String context = contextResult.context();

        List<ChatMessageDto> augmentedMessages = new ArrayList<>();
        augmentedMessages.add(new ChatMessageDto("system", context));
        if (chat.systemPrompt() != null && !chat.systemPrompt().isBlank()) {
            augmentedMessages.add(new ChatMessageDto("system", chat.systemPrompt()));
        }
        ChatMemoryContext memory = resolveMemory(chat, principal);
        augmentedMessages.addAll(toDtoMessages(memory.history()));
        augmentedMessages.addAll(chat.messages());

        ChatRequestDto augmented = new ChatRequestDto(
                chat.provider(),
                null,
                augmentedMessages,
                chat.model(),
                chat.temperature(),
                chat.topP(),
                chat.topK(),
                chat.maxOutputTokens(),
                chat.stopSequences(),
                chat.memory());

        ChatResponse response = chatPort(chat.provider()).chat(toDomainChatRequest(augmented));
        int memoryMessageCount = appendMemory(memory, chat.messages(), response);
        appendConversation(principal, memory, chat.messages().stream().map(this::toDomainMessage).toList(), response);
        boolean exposeDiagnostics = shouldExposeDiagnostics(request);
        Map<String, Object> extraMetadata = memoryMetadata(memory, memoryMessageCount);
        if (exposeDiagnostics && contextResult.diagnostics() != null) {
            extraMetadata.put("ragContextDiagnostics", contextResult.diagnostics().toMetadata());
        }
        return ResponseEntity.ok(ApiResponse.ok(toDto(
                response,
                diagnostics,
                exposeDiagnostics,
                extraMetadata)));
    }

    @GetMapping("/conversations")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read')")
    public ResponseEntity<ApiResponse<List<ConversationSummaryDto>>> conversations(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            Principal principal) {
        String ownerId = conversationChatService.ownerId(principal);
        return ResponseEntity.ok(ApiResponse.ok(conversationChatService.list(ownerId, offset, limit)));
    }

    @GetMapping("/conversations/{conversationId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read')")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> conversation(
            @PathVariable String conversationId,
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                conversationChatService.detail(conversationChatService.ownerId(principal), conversationId)));
    }

    @DeleteMapping("/conversations/{conversationId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteConversation(
            @PathVariable String conversationId,
            Principal principal) {
        boolean deleted = conversationChatService.delete(conversationChatService.ownerId(principal), conversationId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("conversationId", conversationId, "deleted", deleted)));
    }

    @PostMapping("/regenerate")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<ApiResponse<ChatResponseDto>> regenerate(
            @Valid @RequestBody ConversationActionRequestDto request,
            Principal principal) {
        String ownerId = conversationChatService.ownerId(principal);
        List<ChatMessage> messages = conversationChatService.messagesForRegenerate(ownerId, request.conversationId()).stream()
                .map(studio.one.platform.ai.core.chat.ChatConversationMessage::message)
                .toList();
        ChatRequestDto chat = request.chat();
        String provider = chat == null ? null : chat.provider();
        ChatRequest domainRequest = toDomainChatRequest(chat == null ? minimalChatRequest(messages) : chat, messages);
        ChatResponse response = chatPort(provider).chat(domainRequest);
        int messageCount = conversationChatService.replaceLastAssistantResponse(ownerId, request.conversationId(), response);
        return ResponseEntity.ok(ApiResponse.ok(toDto(response, null, false,
                conversationMetadata(request.conversationId(), messageCount))));
    }

    @PostMapping("/truncate")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> truncate(
            @Valid @RequestBody ConversationMessageActionRequestDto request,
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(conversationChatService.truncate(
                conversationChatService.ownerId(principal),
                request.conversationId(),
                request.messageId())));
    }

    @PostMapping("/fork")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> fork(
            @Valid @RequestBody ConversationMessageActionRequestDto request,
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(conversationChatService.fork(
                conversationChatService.ownerId(principal),
                request.conversationId(),
                request.messageId(),
                request.newConversationId())));
    }

    @PostMapping("/compact")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> compact(
            @Valid @RequestBody ConversationActionRequestDto request,
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(conversationChatService.compact(
                conversationChatService.ownerId(principal),
                request.conversationId(),
                request.summary())));
    }

    @PostMapping("/cancel")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<ApiResponse<ConversationDetailDto>> cancel(
            @Valid @RequestBody ConversationActionRequestDto request,
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(conversationChatService.cancel(
                conversationChatService.ownerId(principal),
                request.conversationId())));
    }

    private ChatRequest toDomainChatRequest(ChatRequestDto request) {
        return toDomainChatRequest(request, toDomainMessages(request));
    }

    private ChatRequestDto minimalChatRequest(List<ChatMessage> messages) {
        return new ChatRequestDto(
                null,
                null,
                toDtoMessages(messages),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private ChatRequest toDomainChatRequest(ChatRequestDto request, List<ChatMessage> messages) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(messages);
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

    private ChatPort chatPort(String provider) {
        String normalized = normalizeText(provider);
        try {
            return providerRegistry.chatPort(normalized);
        } catch (IllegalArgumentException ex) {
            if (normalized != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown AI provider: " + normalized, ex);
            }
            throw ex;
        }
    }

    private ObjectScope resolveObjectScope(String objectType, String objectId) {
        String normalizedObjectType = normalizeText(objectType);
        String normalizedObjectId = normalizeText(objectId);
        if (normalizedObjectType == null && normalizedObjectId == null) {
            return ObjectScope.none();
        }
        if (!OBJECT_TYPE_ATTACHMENT.equalsIgnoreCase(normalizedObjectType) || normalizedObjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported RAG object scope");
        }
        return new ObjectScope(OBJECT_TYPE_ATTACHMENT, normalizedObjectId);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<ChatMessage> toDomainMessages(ChatRequestDto request) {
        return toDomainMessages(request, List.of());
    }

    private List<ChatMessage> toDomainMessages(ChatRequestDto request, List<ChatMessage> history) {
        List<ChatMessageDto> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(new ChatMessageDto("system", request.systemPrompt()));
        }
        List<ChatMessage> domainMessages = new ArrayList<>();
        domainMessages.addAll(messages.stream()
                .map(this::toDomainMessage)
                .toList());
        domainMessages.addAll(history);
        domainMessages.addAll(request.messages().stream()
                .map(this::toDomainMessage)
                .toList());
        return domainMessages;
    }

    private List<ChatMessageDto> toDtoMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> new ChatMessageDto(message.role().name().toLowerCase(Locale.ROOT), message.content()))
                .toList();
    }

    private ChatMessage toDomainMessage(ChatMessageDto dto) {
        ChatMessageRole role = ChatMessageRole.valueOf(dto.role().trim().toUpperCase(Locale.ROOT));
        return new ChatMessage(role, dto.content());
    }

    private ChatResponseDto toDto(
            ChatResponse response,
            RagRetrievalDiagnostics diagnostics,
            boolean exposeDiagnostics,
            Map<String, Object> extraMetadata) {
        List<ChatMessageDto> messages = response.messages().stream()
                .map(message -> new ChatMessageDto(message.role().name().toLowerCase(Locale.ROOT), message.content()))
                .toList();
        Map<String, Object> metadata = new HashMap<>(response.metadata());
        metadata.putAll(extraMetadata);
        if (exposeDiagnostics && diagnostics != null) {
            metadata.put("ragDiagnostics", diagnostics.toMetadata());
        }
        return new ChatResponseDto(messages, response.model(), metadata);
    }

    private boolean shouldExposeDiagnostics(ChatRagRequestDto request) {
        return allowClientDebug && Boolean.TRUE.equals(request.debug());
    }

    private String resolveRagQuery(ChatRagRequestDto request) {
        if (request.ragQuery() != null && !request.ragQuery().isBlank()) {
            return request.ragQuery();
        }
        List<ChatMessageDto> messages = request.chat().messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageDto msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.role()) && msg.content() != null && !msg.content().isBlank()) {
                return msg.content();
            }
        }
        throw new IllegalArgumentException("RAG query is empty");
    }

    private List<RagSearchResult> contextExpansionCandidates(
            List<RagSearchResult> ragResults,
            String objectType,
            String objectId,
            int ragTopK,
            boolean resultsAlreadyObjectCandidates) {
        if (!ragContextBuilder.supportsExpansion()
                || objectType == null || objectId == null
                || objectType.isBlank() || objectId.isBlank()) {
            return ragResults;
        }
        if (resultsAlreadyObjectCandidates) {
            return ragResults;
        }
        int limit = contextExpansionCandidateLimit(ragTopK);
        try {
            List<RagSearchResult> candidates = ragPipelineService.listByObject(objectType, objectId, limit);
            return candidates == null || candidates.isEmpty() ? ragResults : candidates;
        } catch (RuntimeException ex) {
            log.warn("RAG context expansion candidate fetch failed for objectType={}, objectId={}: {}",
                    objectType, objectId, ex.getMessage());
            return ragResults;
        }
    }

    private int contextExpansionCandidateLimit(int ragTopK) {
        long requestedLimit = (long) Math.max(ragTopK, 1) * ragContextCandidateMultiplier;
        return (int) Math.min(ragContextMaxCandidates, requestedLimit);
    }

    private int clamp(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private ChatMemoryContext resolveMemory(ChatRequestDto request, Principal principal) {
        ChatMemoryOptionsDto memory = request.memory();
        if (memory == null || !Boolean.TRUE.equals(memory.enabled())) {
            return ChatMemoryContext.disabled();
        }
        if (!chatMemoryEnabled || chatMemoryStore == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chat memory is not enabled on this server");
        }
        String conversationId = normalizeText(memory.conversationId());
        if (conversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "conversationId is required when chat memory is enabled");
        }
        String scopedConversationId = currentPrincipalScope(principal) + ":" + conversationId;
        return new ChatMemoryContext(true, conversationId, scopedConversationId, chatMemoryStore.get(scopedConversationId));
    }

    private int appendMemory(ChatMemoryContext memory, List<ChatMessageDto> requestMessages, ChatResponse response) {
        if (!memory.enabled()) {
            return 0;
        }
        List<ChatMessage> messagesToStore = new ArrayList<>();
        requestMessages.stream()
                .map(this::toDomainMessage)
                .filter(message -> message.role() != ChatMessageRole.SYSTEM)
                .forEach(messagesToStore::add);
        response.messages().stream()
                .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                .forEach(messagesToStore::add);
        return chatMemoryStore.append(memory.storageKey(), messagesToStore);
    }

    private Map<String, Object> memoryMetadata(ChatMemoryContext memory, int memoryMessageCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memoryEnabled", memory.enabled());
        metadata.put(ChatResponseMetadata.KEY_MEMORY_USED, memory.enabled());
        if (memory.enabled()) {
            metadata.put("conversationId", memory.conversationId());
            metadata.put(ChatResponseMetadata.KEY_CONVERSATION_ID, memory.conversationId());
            metadata.put("memoryMessageCount", memoryMessageCount);
        }
        return metadata;
    }

    private Map<String, Object> conversationMetadata(String conversationId, int messageCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("conversationId", conversationId);
        metadata.put(ChatResponseMetadata.KEY_CONVERSATION_ID, conversationId);
        metadata.put("memoryMessageCount", messageCount);
        return metadata;
    }

    private void appendConversation(
            Principal principal,
            ChatMemoryContext memory,
            List<ChatMessage> requestMessages,
            ChatResponse response) {
        if (!memory.enabled() || conversationChatService == null) {
            return;
        }
        conversationChatService.appendTurn(
                conversationChatService.ownerId(principal),
                memory.conversationId(),
                requestMessages,
                response);
    }

    private void writeStreamEvents(
            OutputStream outputStream,
            String requestId,
            ChatPort port,
            ChatRequest request,
            ChatMemoryContext memory,
            List<ChatMessage> requestMessages,
            Principal principal) throws IOException {
        StringBuilder assistant = new StringBuilder();
        ChatStreamEvent last = null;
        boolean streamFailed = false;
        try (java.util.stream.Stream<ChatStreamEvent> events = port.stream(request)) {
            Iterator<ChatStreamEvent> iterator = events.iterator();
            while (iterator.hasNext()) {
                ChatStreamEvent event = iterator.next();
                last = event;
                if (event.type() == ChatStreamEventType.ERROR) {
                    streamFailed = true;
                }
                if (event.type() == ChatStreamEventType.DELTA) {
                    assistant.append(event.delta());
                }
                writeSse(outputStream, requestId, event);
            }
        } catch (RuntimeException ex) {
            ChatStreamEvent error = ChatStreamEvent.error(errorMessage(ex), ChatResponseMetadata.empty());
            writeSse(outputStream, requestId, error);
            return;
        }
        if (!streamFailed && memory.enabled() && assistant.length() > 0) {
            ChatResponse response = new ChatResponse(
                    List.of(ChatMessage.assistant(assistant.toString())),
                    last == null ? "" : last.model(),
                    last == null ? Map.of() : last.metadata().toMap());
            appendMemory(memory, toDtoMessages(requestMessages), response);
            appendConversation(principal, memory, requestMessages, response);
        }
    }

    private void writeSse(OutputStream outputStream, String requestId, ChatStreamEvent event) throws IOException {
        Map<String, Object> payload = new HashMap<>(event.toMap());
        payload.put("requestId", requestId);
        String serialized = "event: " + event.type().value() + "\n"
                + "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        outputStream.write(serialized.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }

    private record ObjectScope(String objectType, String objectId) {

        static ObjectScope none() {
            return new ObjectScope(null, null);
        }

        boolean hasFilter() {
            return objectType != null || objectId != null;
        }
    }

    private String currentPrincipalScope(Principal principal) {
        if (principal != null) {
            String name = normalizeText(principal.getName());
            if (name != null) {
                return "principal:" + name;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Principal name is required when chat memory is enabled");
        }
        return "anonymous";
    }

    private record ChatMemoryContext(boolean enabled, String conversationId, String storageKey, List<ChatMessage> history) {

        static ChatMemoryContext disabled() {
            return new ChatMemoryContext(false, null, null, List.of());
        }
    }
}
