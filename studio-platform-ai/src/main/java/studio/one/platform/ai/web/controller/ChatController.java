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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.dto.ChatResponseDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

/**
 * 채팅 요청을 받아 ChatPort로 위임 후 응답을 DTO로 반환합니다
 * 
 * REST controller exposing chat completions. The base path defaults to {@code /api/ai}
 * and can be overridden with {@code studio.ai.endpoints.base-path}. Requests are
 * delegated to {@link ChatPort} and wrapped with {@link ApiResponse}.
 */
@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/chat")
@Validated
@Slf4j
public class ChatController {

    private final ChatPort chatPort;
    private final RagPipelineService ragPipelineService;

    public ChatController(ChatPort chatPort, RagPipelineService ragPipelineService) {
        this.chatPort = Objects.requireNonNull(chatPort, "chatPort");
        this.ragPipelineService = Objects.requireNonNull(ragPipelineService, "ragPipelineService");
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
        ChatResponse response = chatPort.chat(toDomainChatRequest(request));
        return ResponseEntity.ok(ApiResponse.ok(toDto(response)));
    }

    /**
     * RAG 검색 결과를 시스템 프롬프트로 주입한 뒤 챗을 수행한다.
     */
    @PostMapping("/rag")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write')")
    public ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRag(@Valid @RequestBody ChatRagRequestDto request) {
        
        ChatRequestDto chat = request.chat();
        int ragTopK = request.ragTopK() != null ? request.ragTopK() : 3;
        String objectType = request.objectType();
        String objectId = request.objectId();

        List<RagSearchResult> ragResults;
        String ragQuery = request.ragQuery();
        boolean hasFilter = (objectType != null && !objectType.isBlank()) || (objectId != null && !objectId.isBlank());

        if (ragQuery == null || ragQuery.isBlank()) {
            if (!hasFilter) {
                throw new IllegalArgumentException("ragQuery가 없으면 objectType 또는 objectId를 제공해야 합니다");
            }
            ragResults = ragPipelineService.listByObject(objectType, objectId, ragTopK);
        } else {
            String resolvedQuery = resolveRagQuery(request);
            if (hasFilter) {
                ragResults = ragPipelineService.searchByObject(new RagSearchRequest(resolvedQuery, ragTopK), objectType, objectId);
            } else {
                ragResults = ragPipelineService.search(new RagSearchRequest(resolvedQuery, ragTopK));
            }
        }
        if (log.isInfoEnabled()) {
            log.info("RAG results count={}, objectType={}, objectId={}, ragQuery={}",
                    ragResults.size(), objectType, objectId, ragQuery);
            ragResults.stream().limit(5).forEach(r ->
                    log.info("RAG hit docId={}, score={}, snippet={}",
                            r.documentId(),
                            String.format("%.3f", r.score()),
                            truncate(r.content(), 120)));
        }


        String context = buildContext(ragResults);

        List<ChatMessageDto> augmentedMessages = new ArrayList<>();
        augmentedMessages.add(new ChatMessageDto("system", context));
        augmentedMessages.addAll(chat.messages());

        ChatRequestDto augmented = new ChatRequestDto(
                augmentedMessages,
                chat.model(),
                chat.temperature(),
                chat.topP(),
                chat.topK(),
                chat.maxOutputTokens(),
                chat.stopSequences());

        ChatResponse response = chatPort.chat(toDomainChatRequest(augmented));
        return ResponseEntity.ok(ApiResponse.ok(toDto(response)));
    }

    private ChatRequest toDomainChatRequest(ChatRequestDto request) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(toDomainMessages(request.messages()));
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
    
    private String truncate(String content, int maxLen) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
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

    private String buildContext(List<RagSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "참고할 문서가 없습니다. 일반적으로 답변하세요.";
        }
        StringBuilder sb = new StringBuilder("다음 문서 내용을 참고해 답변하세요:\n");
        for (int i = 0; i < results.size(); i++) {
            RagSearchResult r = results.get(i);
            sb.append("[").append(i + 1).append("] docId=").append(r.documentId())
                    .append(" score=").append(String.format("%.3f", r.score()))
                    .append("\n").append(r.content()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
