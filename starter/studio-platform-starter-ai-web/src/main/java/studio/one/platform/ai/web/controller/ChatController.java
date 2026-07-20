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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.security.Principal;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
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
import studio.one.platform.ai.web.dto.RagRetrievalPolicyDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageDto;
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
    private static final int DEFAULT_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER = 4;
    private static final int DEFAULT_CONTEXT_EXPANSION_MAX_CANDIDATES = 100;
    private static final int MAX_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER = 20;
    private static final int MAX_CONTEXT_EXPANSION_CANDIDATES = 500;
    private static final String RAG_NO_CONTEXT_MESSAGE = "제공된 RAG 문서에서 확인할 수 없습니다.";
    private static final String RAG_SKIP_REASON_NO_RESULTS = "NO_RAG_RESULTS";
    private static final int INTERPRETIVE_MIN_TOP_K = 8;
    private static final double INTERPRETIVE_MAX_MIN_SCORE = 0.55d;
    private static final int MAX_OVERVIEW_SOURCE_CHUNKS = 2_000;
    private static final int MAX_WHOLE_DOCUMENT_CONTEXT_CHARS = 300_000;
    private static final int MAP_REDUCE_OVERVIEW_THRESHOLD_CHARS = 80_000;
    private static final int MAP_REDUCE_SEGMENT_CHARS = 40_000;
    private static final String INTERPRETIVE_ANALYSIS_PROMPT = """
            이 질문은 문서 근거를 종합하는 해석형 질문입니다.
            문서에 결론이나 분류명이 직접 명시되지 않아도 행동, 대화, 감정 표현과 사건을 근거로 합리적으로 추론하세요.
            답변에서는 문서에서 확인되는 사실과 추론을 구분하고, 주요 결론, 핵심 근거, 가능한 대안 해석, 확신도(낮음/중간/높음)를 제시하세요.
            문서에 분류명이 없다는 이유만으로 답변을 거부하지 말고, 관련 근거 자체가 없을 때만 확인할 수 없다고 답하세요.
            """;
    private static final String DOCUMENT_SUMMARY_PROMPT = """
            이 질문은 원본 문서 전체의 요약 또는 줄거리를 요구합니다.
            문서 제목 metadata가 있으면 답변 첫 문장을 '[문서 제목] (파일: [원본 파일명])의 줄거리는 다음과 같습니다.' 형식으로 시작하세요.
            제목만 있으면 제목만, 파일명만 있으면 파일명만 자연스럽게 언급하고, 둘 다 없으면 일반적인 도입 문장으로 시작하세요. 제목이나 파일명을 추측하지 마세요.
            문서의 시작, 주요 전개, 결말을 균형 있게 포함하고 일부 장면이나 문서 안에서 언급되는 영화, 책, 이야기를 원본 전체의 줄거리로 오인하지 마세요.
            원문에 명시되지 않은 장소, 진단, 관계를 추가하거나 원문보다 강하게 단정하지 마세요. 예를 들어 요양 또는 치료 시설을 근거 없이 병원으로 바꾸지 마세요.
            전체 근거가 제공되지 않은 경우에는 부분 요약임을 명시하세요.
            """;
    private static final List<String> DOCUMENT_SUMMARY_METADATA_KEYS = List.of(
            "documentSummary", "abstract", "executiveSummary");
    private static final List<String> KEY_POINTS_METADATA_KEYS = List.of(
            "keyPoints", "keyContent", "highlights", "documentOutline", "outline");

    private final AiProviderRegistry providerRegistry;
    private final RagPipelineService ragPipelineService;
    private final RagChatRetrievalService ragChatRetrievalService;
    private final RagContextBuilder ragContextBuilder;
    private final int ragContextCandidateMultiplier;
    private final int ragContextMaxCandidates;
    private final RagPipelineOptions ragPipelineOptions;
    private final boolean allowClientDebug;
    private final ChatMemoryStore chatMemoryStore;
    private final boolean chatMemoryEnabled;
    private final ConversationChatService conversationChatService;
    private final ObjectMapper objectMapper;
    private final RagRetrievalPolicyStore ragRetrievalPolicyStore;
    private final RagRetrievalPolicyUsageStore ragRetrievalPolicyUsageStore;
    private final AiModelUsageStore modelUsageStore;
    private final RagQueryIntentClassifier ragQueryIntentClassifier = RagQueryIntentClassifier.rules();
    private final RagDocumentOverviewAssembler documentOverviewAssembler = new RagDocumentOverviewAssembler();
    private final RagDocumentMapReduceOverview documentMapReduceOverview = new RagDocumentMapReduceOverview();

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
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug, chatMemoryStore,
                chatMemoryEnabled, conversationChatService, objectMapper,
                ragContextCandidateMultiplier, ragContextMaxCandidates, RagPipelineOptions.defaults());
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
            int ragContextMaxCandidates,
            RagPipelineOptions ragPipelineOptions) {
        this(providerRegistry, ragPipelineService, new RagChatRetrievalService(ragPipelineService), ragContextBuilder,
                allowClientDebug, chatMemoryStore, chatMemoryEnabled, conversationChatService, objectMapper,
                ragContextCandidateMultiplier, ragContextMaxCandidates, ragPipelineOptions, null);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagChatRetrievalService ragChatRetrievalService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            int ragContextCandidateMultiplier,
            int ragContextMaxCandidates,
            RagPipelineOptions ragPipelineOptions) {
        this(providerRegistry, ragPipelineService, ragChatRetrievalService, ragContextBuilder, allowClientDebug,
                chatMemoryStore, chatMemoryEnabled, conversationChatService, objectMapper,
                ragContextCandidateMultiplier, ragContextMaxCandidates, ragPipelineOptions, null);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagChatRetrievalService ragChatRetrievalService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            int ragContextCandidateMultiplier,
            int ragContextMaxCandidates,
            RagPipelineOptions ragPipelineOptions,
            RagRetrievalPolicyStore ragRetrievalPolicyStore) {
        this(providerRegistry, ragPipelineService, ragChatRetrievalService, ragContextBuilder, allowClientDebug,
                chatMemoryStore, chatMemoryEnabled, conversationChatService, objectMapper,
                ragContextCandidateMultiplier, ragContextMaxCandidates, ragPipelineOptions,
                ragRetrievalPolicyStore, null);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagChatRetrievalService ragChatRetrievalService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            int ragContextCandidateMultiplier,
            int ragContextMaxCandidates,
            RagPipelineOptions ragPipelineOptions,
            RagRetrievalPolicyStore ragRetrievalPolicyStore,
            RagRetrievalPolicyUsageStore ragRetrievalPolicyUsageStore) {
        this(providerRegistry, ragPipelineService, ragChatRetrievalService, ragContextBuilder, allowClientDebug,
                chatMemoryStore, chatMemoryEnabled, conversationChatService, objectMapper,
                ragContextCandidateMultiplier, ragContextMaxCandidates, ragPipelineOptions,
                ragRetrievalPolicyStore, ragRetrievalPolicyUsageStore, AiModelUsageStore.noop());
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagChatRetrievalService ragChatRetrievalService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ConversationChatService conversationChatService,
            ObjectMapper objectMapper,
            int ragContextCandidateMultiplier,
            int ragContextMaxCandidates,
            RagPipelineOptions ragPipelineOptions,
            RagRetrievalPolicyStore ragRetrievalPolicyStore,
            RagRetrievalPolicyUsageStore ragRetrievalPolicyUsageStore,
            AiModelUsageStore modelUsageStore) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.ragPipelineService = Objects.requireNonNull(ragPipelineService, "ragPipelineService");
        this.ragChatRetrievalService = Objects.requireNonNull(ragChatRetrievalService, "ragChatRetrievalService");
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
        this.ragPipelineOptions = ragPipelineOptions == null ? RagPipelineOptions.defaults() : ragPipelineOptions;
        this.ragRetrievalPolicyStore = ragRetrievalPolicyStore;
        this.ragRetrievalPolicyUsageStore = ragRetrievalPolicyUsageStore;
        this.modelUsageStore = modelUsageStore == null ? AiModelUsageStore.noop() : modelUsageStore;
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
        ChatResponse response = executeChat(chatPort(request.provider()), toDomainChatRequest(request, domainMessages));
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
        java.util.stream.Stream<ChatStreamEvent> events = openStream(port, domainRequest);
        String requestId = UUID.randomUUID().toString();
        StreamingResponseBody body = outputStream -> writeStreamEvents(
                outputStream,
                requestId,
                events,
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
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write') "
            + "and @endpointAuthz.can('services:ai_rag','read') and "
            + "(#request.objectType() == null or #request.objectType().trim().isEmpty() "
            + "or #request.objectId() == null or #request.objectId().trim().isEmpty() "
            + "or (#request.objectId() != null and !#request.objectId().trim().isEmpty() and "
            + "((#request.objectType().trim().equalsIgnoreCase('attachment') "
            + "and @endpointAuthz.can('features:attachment','read')) "
            + "or (!#request.objectType().trim().equalsIgnoreCase('attachment') "
            + "and (@endpointAuthz.can('objects:' + #request.objectType().trim() + ':' "
            + "+ #request.objectId().trim(),'read') "
            + "or @endpointAuthz.can('objects:' + #request.objectType().trim(),'read'))))))")
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
        long requestStartedNanos = System.nanoTime();
        ChatRequestDto chat = request.chat();
        ObjectScope objectScope = resolveObjectScope(request.objectType(), request.objectId());
        RagRetrievalPolicyDto appliedPolicy = resolveRetrievalPolicy(request, objectScope);
        request = applyRetrievalPolicy(request, appliedPolicy);
        String ragQuery = request.ragQuery();
        RagQueryIntentClassifier.Classification queryIntent = ragQueryIntentClassifier.classify(
                ragQuery == null || ragQuery.isBlank() ? lastUserMessage(chat) : ragQuery);
        request = applyIntentRetrievalPolicy(request, queryIntent);
        int ragTopK = effectiveTopK(request);
        int resultTopK = effectiveResultTopK(request, ragTopK);
        double minScore = effectiveMinScore(request);
        String objectType = objectScope.objectType();
        String objectId = objectScope.objectId();

        List<RagSearchResult> ragResults;
        boolean hasFilter = objectScope.hasFilter();
        String retrievalMode = "SEMANTIC_SEARCH";
        RagChatRetrievalService.RetrievalDebug retrievalDebug = RagChatRetrievalService.RetrievalDebug.disabled();
        long retrievalStartedNanos = System.nanoTime();

        boolean objectCandidateResults = false;
        boolean skipFinalResultLimit = false;
        RagDocumentOverviewAssembler.Assembly documentOverview = null;
        boolean implicitOverviewRequest = (ragQuery == null || ragQuery.isBlank())
                && request.ragTopK() == null
                && request.topK() == null
                && request.retrievalStrategy() == null
                && request.retrievalOptions() == null;
        boolean overviewRequested = hasFilter
                && usesOverviewRetrieval(queryIntent.intent())
                && ((ragQuery != null && !ragQuery.isBlank()) || implicitOverviewRequest);
        if (overviewRequested) {
            List<RagSearchResult> objectResults = ragPipelineService.listByObject(
                    objectType,
                    objectId,
                    contextExpansionCandidateLimit(Math.max(ragTopK, resultTopK)));
            OverviewSelection overview = metadataOverviewResults(objectResults, queryIntent);
            if (overview.metadataUsed()) {
                ragResults = overview.results();
            } else {
                ObjectChunks completeChunks = completeOverviewChunks(
                        objectType, objectId, overview.results());
                OverviewSelection completeOverview = metadataOverviewResults(
                        completeChunks.results(), queryIntent);
                if (completeOverview.metadataUsed()) {
                    overview = completeOverview;
                    ragResults = completeOverview.results();
                } else {
                    ragResults = completeChunks.results();
                    documentOverview = documentOverviewAssembler.assemble(
                            ragResults,
                            MAX_WHOLE_DOCUMENT_CONTEXT_CHARS,
                            completeChunks.complete());
                }
            }
            objectCandidateResults = true;
            skipFinalResultLimit = true;
            retrievalMode = overview.metadataUsed()
                    ? "METADATA_OVERVIEW"
                    : documentOverview.fullCoverage() ? "WHOLE_DOCUMENT_CONTEXT" : "DOCUMENT_COVERAGE_SAMPLES";
        } else if (ragQuery == null || ragQuery.isBlank()) {
            if (!hasFilter) {
                throw new IllegalArgumentException("ragQuery가 없으면 objectType 또는 objectId를 제공해야 합니다");
            }
            ragResults = ragPipelineService.listByObject(objectType, objectId, ragTopK);
            objectCandidateResults = true;
            retrievalMode = "DOCUMENT_CHUNKS";
        } else {
            String resolvedQuery = resolveRagQuery(request);
            RagChatRetrievalService.RetrievalResult retrieval = ragChatRetrievalService.retrieve(
                    request,
                    resolvedQuery,
                    objectType,
                    objectId,
                    ragTopK,
                    minScore,
                    requestedTopK(request),
                    shouldExposeDiagnostics(request));
            ragResults = retrieval.results();
            retrievalDebug = retrieval.debug();
        }
        if (!skipFinalResultLimit) {
            ragResults = limitRagResults(ragResults, resultTopK);
        }
        long retrievalElapsedMs = Math.max(0L, (System.nanoTime() - retrievalStartedNanos) / 1_000_000L);
        recordRetrievalPolicyUsage(appliedPolicy, request, ragResults.size(), ragResults.isEmpty(), retrievalElapsedMs);
        RagRetrievalDiagnostics diagnostics = ragPipelineService.latestDiagnostics().orElse(null);
        boolean exposeDiagnostics = shouldExposeDiagnostics(request);
        if (ragResults.isEmpty()) {
            Map<String, Object> extraMetadata = new LinkedHashMap<>();
            extraMetadata.put("ragReferences", List.of());
            extraMetadata.put("ragSkippedChat", true);
            extraMetadata.put("ragSkipReason", RAG_SKIP_REASON_NO_RESULTS);
            putQueryIntentMetadata(extraMetadata, queryIntent, retrievalMode, ragTopK, minScore);
            if (exposeDiagnostics && retrievalDebug.enabled()) {
                extraMetadata.put("retrieval", retrievalDebug.toMetadata());
            }
            putRetrievalPolicyMetadata(extraMetadata, appliedPolicy);
            ChatResponse response = new ChatResponse(
                    List.of(new ChatMessage(ChatMessageRole.ASSISTANT, RAG_NO_CONTEXT_MESSAGE)),
                    chat.model(),
                    Map.of());
            return ResponseEntity.ok(ApiResponse.ok(toDto(
                    response,
                    diagnostics,
                    exposeDiagnostics,
                    extraMetadata)));
        }

        List<RagSearchResult> expansionCandidates = contextExpansionCandidates(
                ragResults,
                objectType,
                objectId,
                resultTopK,
                objectCandidateResults);
        RagContextBuilder.BuildResult contextResult = documentOverview == null
                ? ragContextBuilder.buildWithDiagnostics(ragResults, expansionCandidates)
                : new RagContextBuilder.BuildResult(
                        documentOverview.context(),
                        null,
                        documentOverview.references());
        String context = contextResult.context();
        long overviewReductionStartedNanos = System.nanoTime();
        RagDocumentMapReduceOverview.Reduction overviewReduction = reduceLargeDocumentOverview(
                chat,
                objectType,
                objectId,
                queryIntent,
                context);
        long overviewReductionElapsedMs = elapsedMillis(overviewReductionStartedNanos);
        context = overviewReduction.context();

        List<ChatMessageDto> augmentedMessages = new ArrayList<>();
        augmentedMessages.add(new ChatMessageDto(
                "system",
                combineRagSystemPrompts(context, chat.systemPrompt(), queryIntent)));
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

        long generationStartedNanos = System.nanoTime();
        ChatResponse response = executeChat(chatPort(chat.provider()), toDomainChatRequest(augmented));
        long generationElapsedMs = elapsedMillis(generationStartedNanos);
        int memoryMessageCount = appendMemory(memory, chat.messages(), response);
        appendConversation(principal, memory, chat.messages().stream().map(this::toDomainMessage).toList(), response);
        Map<String, Object> extraMetadata = memoryMetadata(memory, memoryMessageCount);
        extraMetadata.put("ragReferences", ragReferences(contextResult.usedResults(), exposeDiagnostics));
        if (exposeDiagnostics && contextResult.diagnostics() != null) {
            extraMetadata.put("ragContextDiagnostics", contextResult.diagnostics().toMetadata());
        }
        if (exposeDiagnostics && retrievalDebug.enabled()) {
            extraMetadata.put("retrieval", retrievalDebug.toMetadata());
        }
        putQueryIntentMetadata(extraMetadata, queryIntent, retrievalMode, ragTopK, minScore);
        if (documentOverview != null) {
            extraMetadata.put("overviewSourceChunkCount", documentOverview.sourceChunkCount());
            extraMetadata.put("overviewCoverageStatus", documentOverview.fullCoverage() ? "FULL" : "PARTIAL");
        }
        if (overviewReduction.applied()) {
            extraMetadata.put("overviewReduction", "MAP_REDUCE");
            extraMetadata.put("overviewReductionSegmentCount", overviewReduction.segmentCount());
            extraMetadata.put("overviewReductionCacheHit", overviewReduction.cacheHit());
        }
        long totalElapsedMs = elapsedMillis(requestStartedNanos);
        extraMetadata.put("ragTiming", Map.of(
                "retrievalMs", retrievalElapsedMs,
                "overviewReductionMs", overviewReductionElapsedMs,
                "generationMs", generationElapsedMs,
                "totalMs", totalElapsedMs));
        if (totalElapsedMs >= 10_000L) {
            log.info("Slow RAG chat: intent={}, mode={}, results={}, retrievalMs={}, overviewReductionMs={}, "
                            + "generationMs={}, totalMs={}, overviewCacheHit={}",
                    queryIntent.intent(), retrievalMode, ragResults.size(), retrievalElapsedMs,
                    overviewReductionElapsedMs, generationElapsedMs, totalElapsedMs,
                    overviewReduction.cacheHit());
        }
        putRetrievalPolicyMetadata(extraMetadata, appliedPolicy);
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

    private ChatResponse executeChat(ChatPort port, ChatRequest request) {
        try {
            ChatResponse response = port.chat(request);
            AiModelUsageStore.UsageEstimate estimate = modelUsageStore.record(
                    response.typedMetadata(), response.model());
            Map<String, Object> metadata = new LinkedHashMap<>(response.metadata());
            metadata.put("estimatedCost", estimate.toMetadata());
            return new ChatResponse(response.messages(), response.model(), metadata);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private RagDocumentMapReduceOverview.Reduction reduceLargeDocumentOverview(
            ChatRequestDto chat,
            String objectType,
            String objectId,
            RagQueryIntentClassifier.Classification classification,
            String context) {
        if (!usesOverviewRetrieval(classification.intent())) {
            return RagDocumentMapReduceOverview.Reduction.notApplied(context);
        }
        try {
            ChatPort port = chatPort(chat.provider());
            return documentMapReduceOverview.reduce(
                    objectType,
                    objectId,
                    chat.provider(),
                    chat.model(),
                    context,
                    MAP_REDUCE_OVERVIEW_THRESHOLD_CHARS,
                    MAP_REDUCE_SEGMENT_CHARS,
                    prompt -> segmentSummary(port, chat, prompt));
        } catch (RuntimeException ex) {
            log.warn("Large document map-reduce overview failed; using the original context: {}", ex.getMessage());
            return RagDocumentMapReduceOverview.Reduction.notApplied(context);
        }
    }

    private String segmentSummary(ChatPort port, ChatRequestDto chat, String prompt) {
        ChatRequest.Builder request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.1d)
                .maxOutputTokens(1_200);
        if (chat.model() != null) {
            request.model(chat.model());
        }
        ChatResponse response = executeChat(port, request.build());
        return response.messages().stream()
                .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                .map(ChatMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No assistant segment summary was returned"));
    }

    private java.util.stream.Stream<ChatStreamEvent> openStream(ChatPort port, ChatRequest request) {
        try {
            return port.stream(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private ObjectScope resolveObjectScope(String objectType, String objectId) {
        String normalizedObjectType = normalizeText(objectType);
        String normalizedObjectId = normalizeText(objectId);
        if (normalizedObjectType == null && normalizedObjectId == null) {
            return ObjectScope.none();
        }
        if (normalizedObjectType == null || normalizedObjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "RAG object scope requires both objectType and objectId");
        }
        return new ObjectScope(normalizedObjectType, normalizedObjectId);
    }

    private RagRetrievalPolicyDto resolveRetrievalPolicy(ChatRagRequestDto request, ObjectScope objectScope) {
        if (ragRetrievalPolicyStore == null || !objectScope.hasFilter()) {
            return null;
        }
        String requestedStrategy = normalizeText(request.retrievalStrategy());
        ChatRagRetrievalOptionsDto requestedOptions = request.retrievalOptions();
        if (requestedStrategy != null && requestedOptions != null) {
            return null;
        }
        return ragRetrievalPolicyStore
                .find(objectScope.objectType(), objectScope.objectId())
                .orElse(null);
    }

    private ChatRagRequestDto applyRetrievalPolicy(ChatRagRequestDto request, RagRetrievalPolicyDto policy) {
        if (policy == null) {
            return request;
        }
        String requestedStrategy = normalizeText(request.retrievalStrategy());
        ChatRagRetrievalOptionsDto requestedOptions = request.retrievalOptions();
        String effectiveStrategy = requestedStrategy == null ? normalizeText(policy.retrievalStrategy()) : requestedStrategy;
        ChatRagRetrievalOptionsDto effectiveOptions = requestedOptions == null
                ? policy.retrievalOptions()
                : requestedOptions;
        if (Objects.equals(effectiveStrategy, request.retrievalStrategy())
                && effectiveOptions == request.retrievalOptions()) {
            return request;
        }
        return new ChatRagRequestDto(
                request.chat(),
                request.ragQuery(),
                request.ragTopK(),
                request.objectType(),
                request.objectId(),
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.topK(),
                request.minScore(),
                request.debug(),
                effectiveStrategy,
                effectiveOptions);
    }

    private ChatRagRequestDto applyIntentRetrievalPolicy(
            ChatRagRequestDto request,
            RagQueryIntentClassifier.Classification classification) {
        if (classification.intent() != RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS) {
            return request;
        }
        int topK = Math.max(effectiveTopK(request), INTERPRETIVE_MIN_TOP_K);
        double minScore = Math.min(effectiveMinScore(request), INTERPRETIVE_MAX_MIN_SCORE);
        ChatRagRetrievalOptionsDto options = request.retrievalOptions();
        ChatRagRetrievalOptionsDto adjustedOptions = new ChatRagRetrievalOptionsDto(
                atLeast(options == null ? null : options.structureTopK(), INTERPRETIVE_MIN_TOP_K),
                atLeast(options == null ? null : options.ideaBlockTopK(), INTERPRETIVE_MIN_TOP_K),
                atLeast(options == null ? null : options.finalTopK(), INTERPRETIVE_MIN_TOP_K),
                options == null || options.minScore() == null
                        ? minScore
                        : Math.min(options.minScore(), minScore),
                options == null ? null : options.dedupe(),
                options == null ? null : options.includeDebugChunks(),
                options == null ? null : options.distilledScoreBoost(),
                options == null ? null : options.queryExpansionEnabled());
        return new ChatRagRequestDto(
                request.chat(),
                request.ragQuery(),
                request.ragTopK(),
                request.objectType(),
                request.objectId(),
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                topK,
                minScore,
                request.debug(),
                request.retrievalStrategy(),
                adjustedOptions);
    }

    private Integer atLeast(Integer value, int minimum) {
        return value == null ? minimum : Math.max(value, minimum);
    }

    private void putRetrievalPolicyMetadata(Map<String, Object> metadata, RagRetrievalPolicyDto policy) {
        if (policy == null) {
            return;
        }
        Map<String, Object> policyMetadata = new LinkedHashMap<>();
        policyMetadata.put("applied", true);
        policyMetadata.put("objectType", policy.objectType());
        policyMetadata.put("objectId", policy.objectId());
        policyMetadata.put("retrievalStrategy", policy.retrievalStrategy());
        if (policy.questionSetId() != null) {
            policyMetadata.put("questionSetId", policy.questionSetId());
        }
        if (policy.evaluationRunId() != null) {
            policyMetadata.put("evaluationRunId", policy.evaluationRunId());
        }
        if (policy.score() != null) {
            policyMetadata.put("score", policy.score());
        }
        if (policy.hitRate() != null) {
            policyMetadata.put("hitRate", policy.hitRate());
        }
        if (policy.mrr() != null) {
            policyMetadata.put("mrr", policy.mrr());
        }
        if (policy.averageElapsedMs() != null) {
            policyMetadata.put("averageElapsedMs", policy.averageElapsedMs());
        }
        metadata.put("retrievalPolicy", policyMetadata);
    }

    private void recordRetrievalPolicyUsage(
            RagRetrievalPolicyDto policy,
            ChatRagRequestDto request,
            int resultCount,
            boolean skippedChat,
            long elapsedMs) {
        if (policy == null || ragRetrievalPolicyUsageStore == null) {
            return;
        }
        try {
            ragRetrievalPolicyUsageStore.save(new RagRetrievalPolicyUsageDto(
                    "rpu-" + UUID.randomUUID(),
                    policy.objectType(),
                    policy.objectId(),
                    normalizeText(request.retrievalStrategy()) == null
                            ? policy.retrievalStrategy()
                            : normalizeText(request.retrievalStrategy()),
                    policy.questionSetId(),
                    policy.evaluationRunId(),
                    requestedTopK(request),
                    effectiveMinScore(request),
                    resultCount,
                    skippedChat,
                    elapsedMs,
                    Instant.now()));
        } catch (RuntimeException ex) {
            log.warn("Failed to record RAG retrieval policy usage: objectType={}, objectId={}",
                    policy.objectType(), policy.objectId(), ex);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String combineSystemPrompts(String primary, String secondary) {
        String first = normalizeText(primary);
        String second = normalizeText(secondary);
        if (first == null) {
            return second == null ? "" : second;
        }
        if (second == null) {
            return first;
        }
        return first + "\n\n" + second;
    }

    private String combineRagSystemPrompts(
            String context,
            String clientPrompt,
            RagQueryIntentClassifier.Classification classification) {
        String prompt = combineSystemPrompts(context, clientPrompt);
        if (classification.intent() == RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS) {
            return combineSystemPrompts(prompt, INTERPRETIVE_ANALYSIS_PROMPT);
        }
        if (usesOverviewRetrieval(classification.intent())) {
            return combineSystemPrompts(prompt, DOCUMENT_SUMMARY_PROMPT);
        }
        return prompt;
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
        if (diagnostics != null) {
            metadata.put("ragRetrievalSummary", diagnostics.toMetadata());
        }
        if (exposeDiagnostics && diagnostics != null) {
            metadata.put("ragDiagnostics", diagnostics.toMetadata());
        }
        return new ChatResponseDto(messages, response.model(), metadata);
    }

    private List<Map<String, Object>> ragReferences(List<RagSearchResult> results, boolean includePackedContent) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> references = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            references.add(ragReference(i + 1, results.get(i), includePackedContent));
        }
        return List.copyOf(references);
    }

    private Map<String, Object> ragReference(int index, RagSearchResult result, boolean includePackedContent) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("index", index);
        String documentId = firstText(metadata,
                VectorRecord.KEY_DOCUMENT_ID,
                "documentId",
                "sourceDocumentId");
        if (documentId == null) {
            documentId = result.documentId();
        }
        put(reference, "documentId", documentId);
        String originalFileName = firstText(metadata,
                "sourceFileName", "filename", "fileName", "name", "sourceName");
        String title = firstText(metadata, "documentTitle", "title");
        String sourceName = originalFileName == null ? title : originalFileName;
        put(reference, "sourceName", sourceName == null ? documentId : sourceName);
        put(reference, "originalFileName", originalFileName);
        put(reference, "sourceFileName", originalFileName);
        put(reference, "title", title);
        put(reference, "citationLabel", "근거 " + index);
        String chunkId = firstText(metadata, VectorRecord.KEY_CHUNK_ID, "chunkId");
        put(reference, "chunkId", chunkId == null ? documentId : chunkId);
        put(reference, "chunkOrder", firstInteger(metadata, "chunkOrder", VectorRecord.KEY_CHUNK_INDEX));
        put(reference, "score", result.score());
        if (includePackedContent) {
            put(reference, "content", result.content());
        }
        Integer page = firstInteger(metadata, VectorRecord.KEY_PAGE, "page", "pageNumber");
        if (page != null) {
            reference.put("page", page);
            reference.put("pageNumber", page);
        }
        Integer slide = firstInteger(metadata, VectorRecord.KEY_SLIDE, "slide", "slideNumber");
        if (slide != null) {
            reference.put("slide", slide);
            reference.put("slideNumber", slide);
        }
        put(reference, "section", firstText(metadata, "section", VectorRecord.KEY_HEADING_PATH, "headingPath"));
        put(reference, "heading", firstText(metadata, "heading", VectorRecord.KEY_HEADING_PATH, "headingPath"));
        put(reference, "sourceRef", firstText(metadata, "sourceRef"));
        return Map.copyOf(reference);
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFC);
            }
            if (value != null && !(value instanceof String)) {
                String text = value.toString();
                if (!text.isBlank()) {
                    return java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFC);
                }
            }
        }
        return null;
    }

    private Integer firstInteger(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Integer value = integer(metadata.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean shouldExposeDiagnostics(ChatRagRequestDto request) {
        return allowClientDebug && Boolean.TRUE.equals(request.debug());
    }

    private int effectiveTopK(ChatRagRequestDto request) {
        Integer requestedTopK = requestedTopK(request);
        return requestedTopK == null ? ragPipelineOptions.topK() : requestedTopK;
    }

    private int effectiveResultTopK(ChatRagRequestDto request, int fallback) {
        if (request.retrievalOptions() != null && request.retrievalOptions().finalTopK() != null) {
            return request.retrievalOptions().finalTopK();
        }
        return fallback;
    }

    private Integer requestedTopK(ChatRagRequestDto request) {
        return request.topK() != null ? request.topK() : request.ragTopK();
    }

    private double effectiveMinScore(ChatRagRequestDto request) {
        if (request.minScore() != null) {
            return request.minScore();
        }
        if (request.retrievalOptions() != null && request.retrievalOptions().minScore() != null) {
            return request.retrievalOptions().minScore();
        }
        return ragPipelineOptions.minScore();
    }

    private List<RagSearchResult> limitRagResults(List<RagSearchResult> results, int topK) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .limit(Math.max(topK, 0))
                .toList();
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

    private String lastUserMessage(ChatRequestDto chat) {
        if (chat == null || chat.messages() == null) {
            return null;
        }
        for (int i = chat.messages().size() - 1; i >= 0; i--) {
            ChatMessageDto message = chat.messages().get(i);
            if ("user".equalsIgnoreCase(message.role())) {
                return message.content();
            }
        }
        return null;
    }

    private OverviewSelection metadataOverviewResults(
            List<RagSearchResult> objectResults,
            RagQueryIntentClassifier.Classification classification) {
        if (objectResults == null || objectResults.isEmpty()) {
            return new OverviewSelection(List.of(), false);
        }
        List<String> keys = new ArrayList<>();
        if (classification.intent() == RagQueryIntentClassifier.Intent.KEY_POINTS) {
            keys.addAll(KEY_POINTS_METADATA_KEYS);
            keys.addAll(DOCUMENT_SUMMARY_METADATA_KEYS);
        } else {
            keys.addAll(DOCUMENT_SUMMARY_METADATA_KEYS);
            keys.addAll(KEY_POINTS_METADATA_KEYS);
        }
        for (RagSearchResult result : objectResults) {
            for (String key : keys) {
                String content = metadataText(metadataValue(result.metadata(), key));
                if (content != null) {
                    Map<String, Object> metadata = new LinkedHashMap<>(result.metadata());
                    metadata.put("overviewMetadataKey", key);
                    metadata.put("overviewMetadataUsed", true);
                    return new OverviewSelection(
                            List.of(new RagSearchResult(
                                    result.documentId() + ":" + key,
                                    content,
                                    metadata,
                                    1.0d)),
                            true);
                }
            }
        }
        return new OverviewSelection(objectResults, false);
    }

    private ObjectChunks completeOverviewChunks(
            String objectType,
            String objectId,
            List<RagSearchResult> initialResults) {
        List<RagSearchResult> initial = initialResults == null ? List.of() : initialResults;
        long total = ragPipelineService.countByObject(objectType, objectId);
        if (total <= initial.size() || total <= 0L) {
            return new ObjectChunks(initial, true);
        }
        int requested = (int) Math.min(total, MAX_OVERVIEW_SOURCE_CHUNKS);
        int pageSize = Math.max(1, Math.min(ragPipelineOptions.maxListLimit(), requested));
        List<RagSearchResult> all = new ArrayList<>(requested);
        for (int offset = 0; offset < requested; offset += pageSize) {
            int size = Math.min(pageSize, requested - offset);
            List<RagSearchResult> page = ragPipelineService.listByObject(objectType, objectId, offset, size);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < size) {
                break;
            }
        }
        if (all.isEmpty()) {
            return new ObjectChunks(initial, false);
        }
        return new ObjectChunks(List.copyOf(all), all.size() >= total);
    }

    private boolean usesOverviewRetrieval(RagQueryIntentClassifier.Intent intent) {
        return intent == RagQueryIntentClassifier.Intent.DOCUMENT_SUMMARY
                || intent == RagQueryIntentClassifier.Intent.KEY_POINTS;
    }

    private Object metadataValue(Map<String, Object> metadata, String key) {
        Object direct = metadata.get(key);
        if (direct != null) {
            return direct;
        }
        for (String containerKey : List.of("documentMetadata", "documentOverview", "overview")) {
            Object container = metadata.get(containerKey);
            if (container instanceof Map<?, ?> values && values.get(key) != null) {
                return values.get(key);
            }
        }
        return null;
    }

    private String metadataText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return normalizeText(text);
        }
        if (value instanceof Iterable<?> values) {
            List<String> lines = new ArrayList<>();
            for (Object item : values) {
                String text = normalizeText(Objects.toString(item, null));
                if (text != null) {
                    lines.add("- " + text);
                }
            }
            return lines.isEmpty() ? null : String.join("\n", lines);
        }
        try {
            return normalizeText(objectMapper.writeValueAsString(value));
        } catch (IOException ex) {
            return normalizeText(Objects.toString(value, null));
        }
    }

    private void putQueryIntentMetadata(
            Map<String, Object> metadata,
            RagQueryIntentClassifier.Classification classification,
            String retrievalMode,
            int retrievalTopK,
            double retrievalMinScore) {
        metadata.put("ragQueryIntent", classification.intent().name());
        metadata.put("ragQueryIntentConfidence", classification.confidence());
        metadata.put("ragQueryIntentReason", classification.reason());
        metadata.put("ragRetrievalMode", retrievalMode);
        metadata.put("ragRetrievalTopK", retrievalTopK);
        metadata.put("ragRetrievalMinScore", retrievalMinScore);
        if (classification.intent() == RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS) {
            metadata.put("answerType", "EVIDENCE_BASED_INFERENCE");
            metadata.put("ragInferenceEnabled", true);
        }
    }

    private record OverviewSelection(List<RagSearchResult> results, boolean metadataUsed) {
    }

    private record ObjectChunks(List<RagSearchResult> results, boolean complete) {
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
            java.util.stream.Stream<ChatStreamEvent> events,
            ChatMemoryContext memory,
            List<ChatMessage> requestMessages,
            Principal principal) throws IOException {
        StringBuilder assistant = new StringBuilder();
        ChatStreamEvent last = null;
        boolean streamFailed = false;
        try (events) {
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
        if (!streamFailed && last != null) {
            modelUsageStore.record(last.metadata(), last.model());
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
