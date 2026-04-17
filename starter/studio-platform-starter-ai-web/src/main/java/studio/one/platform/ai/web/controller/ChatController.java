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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMemoryStore;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
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

    private static final String OBJECT_TYPE_ATTACHMENT = "attachment";

    private final AiProviderRegistry providerRegistry;
    private final RagPipelineService ragPipelineService;
    private final RagContextBuilder ragContextBuilder;
    private final boolean allowClientDebug;
    private final ChatMemoryStore chatMemoryStore;
    private final boolean chatMemoryEnabled;

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
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.ragPipelineService = Objects.requireNonNull(ragPipelineService, "ragPipelineService");
        this.ragContextBuilder = Objects.requireNonNull(ragContextBuilder, "ragContextBuilder");
        this.allowClientDebug = allowClientDebug;
        this.chatMemoryStore = chatMemoryStore;
        this.chatMemoryEnabled = chatMemoryEnabled;
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
    public ResponseEntity<ApiResponse<ChatResponseDto>> chat(@Valid @RequestBody ChatRequestDto request) {
        ChatMemoryContext memory = resolveMemory(request);
        ChatResponse response = chatPort(request.provider()).chat(toDomainChatRequest(request, toDomainMessages(request, memory.history())));
        int memoryMessageCount = appendMemory(memory, request.messages(), response);
        return ResponseEntity.ok(ApiResponse.ok(toDto(response, null, false, memoryMetadata(memory, memoryMessageCount))));
    }

    /**
     * RAG 검색 결과를 시스템 프롬프트로 주입한 뒤 챗을 수행한다.
     */
    @PostMapping("/rag")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write') and "
            + "(#request.objectType() == null or !#request.objectType().trim().equalsIgnoreCase('attachment') "
            + "or @endpointAuthz.can('features:attachment','read'))")
    public ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRag(@Valid @RequestBody ChatRagRequestDto request) {
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
                ragResults = ragPipelineService.searchByObject(
                        new RagSearchRequest(resolvedQuery, ragTopK), objectType, objectId);
            } else {
                ragResults = ragPipelineService.search(new RagSearchRequest(resolvedQuery, ragTopK));
            }
        }
        RagRetrievalDiagnostics diagnostics = ragPipelineService.latestDiagnostics().orElse(null);

        String context = ragContextBuilder.build(ragResults);

        List<ChatMessageDto> augmentedMessages = new ArrayList<>();
        augmentedMessages.add(new ChatMessageDto("system", context));
        if (chat.systemPrompt() != null && !chat.systemPrompt().isBlank()) {
            augmentedMessages.add(new ChatMessageDto("system", chat.systemPrompt()));
        }
        ChatMemoryContext memory = resolveMemory(chat);
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
        return ResponseEntity.ok(ApiResponse.ok(toDto(
                response,
                diagnostics,
                shouldExposeDiagnostics(request),
                memoryMetadata(memory, memoryMessageCount))));
    }

    private ChatRequest toDomainChatRequest(ChatRequestDto request) {
        return toDomainChatRequest(request, toDomainMessages(request));
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

    private ChatResponseDto toDto(ChatResponse response) {
        return toDto(response, null, false, Map.of("memoryEnabled", false));
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

    private ChatMemoryContext resolveMemory(ChatRequestDto request) {
        ChatMemoryOptionsDto memory = request.memory();
        if (memory == null || !Boolean.TRUE.equals(memory.enabled())) {
            return ChatMemoryContext.disabled();
        }
        String conversationId = normalizeText(memory.conversationId());
        if (conversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "conversationId is required when chat memory is enabled");
        }
        if (!chatMemoryEnabled || chatMemoryStore == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chat memory is not enabled on this server");
        }
        return new ChatMemoryContext(true, conversationId, chatMemoryStore.get(conversationId));
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
        return chatMemoryStore.append(memory.conversationId(), messagesToStore);
    }

    private Map<String, Object> memoryMetadata(ChatMemoryContext memory, int memoryMessageCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memoryEnabled", memory.enabled());
        if (memory.enabled()) {
            metadata.put("conversationId", memory.conversationId());
            metadata.put("memoryMessageCount", memoryMessageCount);
        }
        return metadata;
    }

    private record ObjectScope(String objectType, String objectId) {

        static ObjectScope none() {
            return new ObjectScope(null, null);
        }

        boolean hasFilter() {
            return objectType != null || objectId != null;
        }
    }

    private record ChatMemoryContext(boolean enabled, String conversationId, List<ChatMessage> history) {

        static ChatMemoryContext disabled() {
            return new ChatMemoryContext(false, null, List.of());
        }
    }
}
