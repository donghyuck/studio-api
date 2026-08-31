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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
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
import studio.one.platform.ai.web.cache.RagAnswerCache;
import studio.one.platform.ai.web.cache.RagAnswerCacheKey;
import studio.one.platform.ai.web.cache.RagCachedAnswer;
import studio.one.platform.ai.web.dto.ConversationActionRequestDto;
import studio.one.platform.ai.web.dto.ConversationDetailDto;
import studio.one.platform.ai.web.dto.ConversationMessageDto;
import studio.one.platform.ai.web.dto.ConversationMessageActionRequestDto;
import studio.one.platform.ai.web.dto.ConversationSummaryDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageDto;
import studio.one.platform.ai.web.dto.RagAnswerPolicyCapabilitiesDto;
import studio.one.platform.ai.web.dto.RagChatCapabilitiesDto;
import studio.one.platform.ai.web.dto.RagQuestionSuggestionCapabilitiesDto;
import studio.one.platform.ai.web.dto.IndexedWebCapabilitiesDto;
import studio.one.platform.ai.web.dto.RagRegenerateRequestDto;
import studio.one.platform.ai.web.dto.RagSourcePolicyCapabilitiesDto;
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
    private static final int INDEXED_WEB_PAGE_CANDIDATE_CAP = 2;
    private static final int MAX_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER = 20;
    private static final int MAX_CONTEXT_EXPANSION_CANDIDATES = 500;
    private static final String RAG_NO_RESULTS_MESSAGE = "검색 기준을 통과한 문서 구간이 없습니다.";
    private static final String RAG_NO_PACKED_EVIDENCE_MESSAGE =
            "관련 구간은 찾았지만 답변 근거로 구성하지 못했습니다.";
    private static final String RAG_SKIP_REASON_NO_RESULTS = "NO_RAG_RESULTS";
    private static final String RAG_SKIP_REASON_NO_PACKED_EVIDENCE = "NO_PACKED_EVIDENCE";
    private static final String RAG_SKIP_REASON_INSUFFICIENT_SOURCE_COVERAGE =
            "INSUFFICIENT_SOURCE_COVERAGE";
    private static final String RAG_INSUFFICIENT_SOURCE_COVERAGE_MESSAGE =
            "문서와 외부 자료를 비교하려면 양쪽에서 인용 가능한 근거가 모두 필요합니다. 현재는 한쪽 근거가 부족합니다.";
    private static final int INTERPRETIVE_MIN_TOP_K = 8;
    private static final double INTERPRETIVE_MAX_MIN_SCORE = 0.55d;
    private static final int MAX_OVERVIEW_SOURCE_CHUNKS = 2_000;
    private static final int MAX_WHOLE_DOCUMENT_CONTEXT_CHARS = 300_000;
    private static final int MAP_REDUCE_OVERVIEW_THRESHOLD_CHARS = 80_000;
    private static final int MAP_REDUCE_SEGMENT_CHARS = 40_000;
    private static final String INTERPRETIVE_ANALYSIS_PROMPT = """
            이 질문은 문서 근거를 종합하는 해석형 질문입니다.
            문서에 결론이나 분류명이 직접 명시되지 않아도 행동, 대화, 감정 표현과 사건을 근거로 합리적으로 추론하세요.
            제목, 번호 목록, 서론, 맺음말 없이 정확히 한 문단으로만 답하세요.
            문단 안에서 '문서 사실:'과 '해석:'을 명시해 직접 사실과 해석을 구분하고, 필요하면 '확신도:'를 덧붙이세요.
            각 문장은 줄바꿈 없이 작성하고 문장 끝에 반드시 이를 뒷받침하는 인용을 붙이세요.
            뒷받침할 근거가 없는 문장은 만들지 마세요.
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

    private final ModelDeploymentRegistry providerRegistry;
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
    private final RagAnswerCache ragAnswerCache;
    private final RagAnswerPolicyResolver ragAnswerPolicyResolver;
    private final RagAnswerPromptComposer ragAnswerPromptComposer;
    private final RagAnswerFinalizer ragAnswerFinalizer;
    private RagSourcePolicyResolver ragSourcePolicyResolver;
    private RagExternalEvidenceService ragExternalEvidenceService;
    private RagObjectAuthorizationRouter ragObjectAuthorizationRouter;
    private List<IndexedRagSourceProvider> indexedRagSourceProviders = List.of();
    private final RagQueryIntentClassifier ragQueryIntentClassifier = RagQueryIntentClassifier.rules();
    private boolean questionSuggestionsEnabled;
    private final RagDocumentOverviewAssembler documentOverviewAssembler = new RagDocumentOverviewAssembler();
    private final RagDocumentMapReduceOverview documentMapReduceOverview = new RagDocumentMapReduceOverview();

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            ObjectMapper objectMapper) {
        this(providerRegistry, ragPipelineService, RagContextBuilder.defaults(), objectMapper);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            ObjectMapper objectMapper) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, false, objectMapper);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ObjectMapper objectMapper) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug, null, false, objectMapper);
    }

    public ChatController(
            AiProviderRegistry providerRegistry,
            RagPipelineService ragPipelineService,
            RagContextBuilder ragContextBuilder,
            boolean allowClientDebug,
            ChatMemoryStore chatMemoryStore,
            boolean chatMemoryEnabled,
            ObjectMapper objectMapper) {
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug,
                chatMemoryStore, chatMemoryEnabled, null, objectMapper);
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
        this(providerRegistry, ragPipelineService, ragContextBuilder, allowClientDebug,
                chatMemoryStore, chatMemoryEnabled, conversationChatService, objectMapper,
                DEFAULT_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER,
                DEFAULT_CONTEXT_EXPANSION_MAX_CANDIDATES);
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
            ModelDeploymentRegistry providerRegistry,
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
            ModelDeploymentRegistry providerRegistry,
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
            ModelDeploymentRegistry providerRegistry,
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
            ModelDeploymentRegistry providerRegistry,
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
        this(providerRegistry, ragPipelineService, ragChatRetrievalService, ragContextBuilder,
                allowClientDebug, chatMemoryStore, chatMemoryEnabled, conversationChatService, objectMapper,
                ragContextCandidateMultiplier, ragContextMaxCandidates, ragPipelineOptions,
                ragRetrievalPolicyStore, ragRetrievalPolicyUsageStore, modelUsageStore, RagAnswerCache.noop());
    }

    public ChatController(
            ModelDeploymentRegistry providerRegistry,
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
            AiModelUsageStore modelUsageStore,
            RagAnswerCache ragAnswerCache) {
        this(
                providerRegistry,
                ragPipelineService,
                ragChatRetrievalService,
                ragContextBuilder,
                allowClientDebug,
                chatMemoryStore,
                chatMemoryEnabled,
                conversationChatService,
                objectMapper,
                ragContextCandidateMultiplier,
                ragContextMaxCandidates,
                ragPipelineOptions,
                ragRetrievalPolicyStore,
                ragRetrievalPolicyUsageStore,
                modelUsageStore,
                ragAnswerCache,
                RagAnswerPolicyResolver.defaults(),
                new RagAnswerPromptComposer(),
                new RagAnswerFinalizer());
    }

    public ChatController(
            ModelDeploymentRegistry providerRegistry,
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
            AiModelUsageStore modelUsageStore,
            RagAnswerCache ragAnswerCache,
            RagAnswerPolicyResolver ragAnswerPolicyResolver,
            RagAnswerPromptComposer ragAnswerPromptComposer,
            RagAnswerFinalizer ragAnswerFinalizer) {
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
        this.ragAnswerCache = ragAnswerCache == null ? RagAnswerCache.noop() : ragAnswerCache;
        this.ragAnswerPolicyResolver = ragAnswerPolicyResolver == null
                ? RagAnswerPolicyResolver.defaults()
                : ragAnswerPolicyResolver;
        this.ragAnswerPromptComposer = ragAnswerPromptComposer == null
                ? new RagAnswerPromptComposer()
                : ragAnswerPromptComposer;
        this.ragAnswerFinalizer = ragAnswerFinalizer == null
                ? new RagAnswerFinalizer()
                : ragAnswerFinalizer;
        this.ragSourcePolicyResolver = RagSourcePolicyResolver.defaults();
        this.ragExternalEvidenceService = RagExternalEvidenceService.unavailable();
    }

    public ChatController(
            ModelDeploymentRegistry providerRegistry,
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
            AiModelUsageStore modelUsageStore,
            RagAnswerCache ragAnswerCache,
            RagAnswerPolicyResolver ragAnswerPolicyResolver,
            RagAnswerPromptComposer ragAnswerPromptComposer,
            RagAnswerFinalizer ragAnswerFinalizer,
            RagObjectAuthorizationRouter ragObjectAuthorizationRouter) {
        this(
                providerRegistry,
                ragPipelineService,
                ragChatRetrievalService,
                ragContextBuilder,
                allowClientDebug,
                chatMemoryStore,
                chatMemoryEnabled,
                conversationChatService,
                objectMapper,
                ragContextCandidateMultiplier,
                ragContextMaxCandidates,
                ragPipelineOptions,
                ragRetrievalPolicyStore,
                ragRetrievalPolicyUsageStore,
                modelUsageStore,
                ragAnswerCache,
                ragAnswerPolicyResolver,
                ragAnswerPromptComposer,
                ragAnswerFinalizer);
        this.ragObjectAuthorizationRouter = ragObjectAuthorizationRouter;
    }

    public ChatController(
            ModelDeploymentRegistry providerRegistry,
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
            AiModelUsageStore modelUsageStore,
            RagAnswerCache ragAnswerCache,
            RagAnswerPolicyResolver ragAnswerPolicyResolver,
            RagAnswerPromptComposer ragAnswerPromptComposer,
            RagAnswerFinalizer ragAnswerFinalizer,
            RagObjectAuthorizationRouter ragObjectAuthorizationRouter,
            RagSourcePolicyResolver ragSourcePolicyResolver,
            RagExternalEvidenceService ragExternalEvidenceService) {
        this(
                providerRegistry,
                ragPipelineService,
                ragChatRetrievalService,
                ragContextBuilder,
                allowClientDebug,
                chatMemoryStore,
                chatMemoryEnabled,
                conversationChatService,
                objectMapper,
                ragContextCandidateMultiplier,
                ragContextMaxCandidates,
                ragPipelineOptions,
                ragRetrievalPolicyStore,
                ragRetrievalPolicyUsageStore,
                modelUsageStore,
                ragAnswerCache,
                ragAnswerPolicyResolver,
                ragAnswerPromptComposer,
                ragAnswerFinalizer,
                ragObjectAuthorizationRouter);
        this.ragSourcePolicyResolver = ragSourcePolicyResolver == null
                ? RagSourcePolicyResolver.defaults()
                : ragSourcePolicyResolver;
        this.ragExternalEvidenceService = ragExternalEvidenceService == null
                ? RagExternalEvidenceService.unavailable()
                : ragExternalEvidenceService;
    }

    public void setIndexedRagSourceProviders(List<IndexedRagSourceProvider> providers) {
        this.indexedRagSourceProviders = providers == null ? List.of() : List.copyOf(providers);
    }

    public void setQuestionSuggestionsEnabled(boolean enabled) {
        this.questionSuggestionsEnabled = enabled;
    }

    private int indexedWebMaxSources() {
        return indexedRagSourceProviders.stream()
                .mapToInt(IndexedRagSourceProvider::maxSelectedSources)
                .min()
                .orElse(10);
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
        ChatResponse response = executeChat(chatPort(deploymentOrProvider(request)),
                toDomainChatRequest(request, domainMessages),
                AiModelUsageRequestKind.CHAT);
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
        ChatPort port = chatPort(deploymentOrProvider(request));
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
            + "and @endpointAuthz.can('services:ai_rag','read') "
            + "and @ragObjectAuthorizationRouter.canRead(#request)")
    public ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRag(
            @Valid @RequestBody ChatRagRequestDto request,
            Principal principal) {
        return chatWithRagInternal(request, principal);
    }

    @PostMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write') "
            + "and @endpointAuthz.can('services:ai_rag','read') "
            + "and @ragObjectAuthorizationRouter.canRead(#request)")
    public ResponseEntity<StreamingResponseBody> streamWithRag(
            @Valid @RequestBody ChatRagRequestDto request,
            Principal principal) {
        RagAnswerMode.parse(request.answerMode());
        RagSourceScope.parse(request.sourceScope());
        String requestId = UUID.randomUUID().toString();
        StreamingResponseBody body = outputStream ->
                writeRagStreamRequest(outputStream, requestId, request, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    @GetMapping("/rag/answer-policy")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagAnswerPolicyCapabilitiesDto>> ragAnswerPolicy() {
        return ResponseEntity.ok(ApiResponse.ok(answerPolicyCapabilities()));
    }

    private RagAnswerPolicyCapabilitiesDto answerPolicyCapabilities() {
        List<String> availableModes = java.util.Arrays.stream(RagAnswerMode.values())
                .filter(mode -> !mode.isMorePermissiveThan(ragAnswerPolicyResolver.maximumMode()))
                .map(Enum::name)
                .toList();
        return new RagAnswerPolicyCapabilitiesDto(
                ragAnswerPolicyResolver.defaultMode().name(),
                ragAnswerPolicyResolver.maximumMode().name(),
                ragAnswerPolicyResolver.clientSelectionEnabled(),
                ragAnswerPolicyResolver.policyVersion(),
                availableModes);
    }

    @GetMapping("/rag/capabilities")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<RagChatCapabilitiesDto>> ragCapabilities() {
        RagSourcePolicyCapabilitiesDto sourcePolicy = new RagSourcePolicyCapabilitiesDto(
                ragSourcePolicyResolver.defaultScope().name(),
                ragSourcePolicyResolver.maximumScope().name(),
                ragSourcePolicyResolver.clientSelectionEnabled(),
                ragSourcePolicyResolver.externalProviderAvailable(),
                ragSourcePolicyResolver.policyVersion(),
                ragSourcePolicyResolver.availableScopes());
        return ResponseEntity.ok(ApiResponse.ok(new RagChatCapabilitiesDto(
                answerPolicyCapabilities(),
                sourcePolicy,
                indexedWebCapabilities(),
                new RagQuestionSuggestionCapabilitiesDto(
                        questionSuggestionsEnabled,
                        DocumentQuestionSuggestionPolicy.CONTRACT_VERSION,
                        DocumentQuestionSuggestionPolicy.MAX_SUGGESTIONS))));
    }

    private IndexedWebCapabilitiesDto indexedWebCapabilities() {
        return indexedRagSourceProviders.stream()
                .filter(provider -> provider.supports("web_source"))
                .findFirst()
                .map(studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider::capabilities)
                .map(capabilities -> new IndexedWebCapabilitiesDto(
                        capabilities.enabled(),
                        capabilities.maxSelectedSources(),
                        capabilities.supportedSchemes(),
                        capabilities.maxUrlLength(),
                        capabilities.collectionModes(),
                        capabilities.siteCrawlEnabled(),
                        capabilities.defaultMaxDepth(),
                        capabilities.maximumDepth(),
                        capabilities.defaultMaxPages(),
                        capabilities.maximumPages(),
                        capabilities.defaultMaxConcurrency(),
                        capabilities.maximumConcurrency(),
                        capabilities.discoveryModes()))
                .orElseGet(() -> new IndexedWebCapabilitiesDto(false, 0, List.of(), 2048));
    }

    ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRag(ChatRagRequestDto request) {
        return chatWithRagInternal(request, null);
    }

    private ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRagInternal(
            ChatRagRequestDto request,
            Principal principal) {
        return chatWithRagInternal(request, principal, null);
    }

    private ResponseEntity<ApiResponse<ChatResponseDto>> chatWithRagInternal(
            ChatRagRequestDto request,
            Principal principal,
            String replaceConversationId) {
        PreparedRagChat prepared = prepareRagChat(request, principal);
        if (prepared.skippedChat()) {
            RagAnswerOutcome outcome = skippedOutcome(prepared);
            String skippedMessage = skippedMessage(outcome);
            ChatResponse response = new ChatResponse(
                    List.of(new ChatMessage(ChatMessageRole.ASSISTANT, skippedMessage)),
                    prepared.chat().model(),
                    Map.of());
            response = responseWithMetadata(response, ragTurnMetadata(
                    prepared,
                    skippedMessage,
                    RagCitationValidator.Status.NO_PACKED_EVIDENCE.name(),
                    RagAnswerPolicyValidator.Status.NO_PACKED_EVIDENCE.name(),
                    "BYPASS",
                    outcome));
            Map<String, Object> metadata = new LinkedHashMap<>(skippedRagMetadata(prepared));
            if (replaceConversationId != null && !replaceConversationId.isBlank()) {
                metadata.putAll(persistRagConversation(
                        principal, prepared, response, replaceConversationId));
            }
            return ResponseEntity.ok(ApiResponse.ok(toDto(
                    response,
                    prepared.diagnostics(),
                    prepared.exposeDiagnostics(),
                    metadata)));
        }

        Optional<CachedRagHit> cached = cachedRagAnswer(prepared, principal);
        if (cached.isPresent()) {
            CachedRagHit hit = cached.get();
            RagAnswerFinalizer.FinalizedAnswer finalized = ragAnswerFinalizer.finalizeAnswer(
                    hit.answer().canonicalContent(),
                    prepared.evidenceSet(),
                    prepared.answerPolicy(),
                    prepared.queryIntent());
            finalized = withCachedOutcome(finalized, hit.answer());
            ChatResponse response = new ChatResponse(
                    List.of(ChatMessage.assistant(finalized.canonicalContent())),
                    hit.answer().model(),
                    Map.of());
            response = responseWithMetadata(response, ragTurnMetadata(
                    prepared,
                    finalized.canonicalContent(),
                    finalized.validation().status().name(),
                    finalized.policyValidation().status().name(),
                    "HIT",
                    finalized.outcome()));
            Map<String, Object> extraMetadata = new LinkedHashMap<>(persistRagConversation(
                    principal, prepared, response, replaceConversationId));
            extraMetadata.putAll(withRagTiming(prepared, 0L));
            extraMetadata.putAll(ragOutcomeMetadata(prepared, finalized.outcome()));
            extraMetadata.put("canonicalContent", finalized.canonicalContent());
            extraMetadata.put("citationValidationStatus", finalized.validation().status().name());
            extraMetadata.put("answerPolicyValidationStatus", finalized.policyValidation().status().name());
            extraMetadata.put("ragAnswerCache", "HIT");
            return ResponseEntity.ok(ApiResponse.ok(toDto(
                    response,
                    prepared.diagnostics(),
                    prepared.exposeDiagnostics(),
                    extraMetadata)));
        }

        long generationStartedNanos = System.nanoTime();
        ChatResponse response = executeChat(
                chatPort(deploymentOrProvider(prepared.chat())),
                toDomainChatRequest(prepared.augmentedChat()),
                AiModelUsageRequestKind.RAG);
        long generationElapsedMs = elapsedMillis(generationStartedNanos);
        RagAnswerFinalizer.FinalizedAnswer finalized =
                finalizeRagResponse(
                        response,
                        prepared.evidenceSet(),
                        prepared.answerPolicy(),
                        prepared.queryIntent());
        response = canonicalResponse(response, finalized.canonicalContent());
        response = responseWithMetadata(response, ragTurnMetadata(
                prepared,
                finalized.canonicalContent(),
                finalized.validation().status().name(),
                finalized.policyValidation().status().name(),
                ragAnswerCache.enabled() ? "MISS" : "BYPASS",
                finalized.outcome()));
        Map<String, Object> extraMetadata = new LinkedHashMap<>(persistRagConversation(
                principal, prepared, response, replaceConversationId));
        extraMetadata.putAll(withRagTiming(prepared, generationElapsedMs));
        extraMetadata.put("canonicalContent", finalized.canonicalContent());
        extraMetadata.put("citationValidationStatus", finalized.validation().status().name());
        extraMetadata.put("answerPolicyValidationStatus", finalized.policyValidation().status().name());
        extraMetadata.put("ragAnswerCache", ragAnswerCache.enabled() ? "MISS" : "BYPASS");
        extraMetadata.putAll(ragOutcomeMetadata(prepared, finalized.outcome()));
        cacheRagAnswer(prepared, principal, response.model(), finalized);
        logSlowRagChat(prepared, generationElapsedMs);
        return ResponseEntity.ok(ApiResponse.ok(toDto(
                response,
                prepared.diagnostics(),
                prepared.exposeDiagnostics(),
                extraMetadata)));
    }

    private PreparedRagChat prepareRagChat(ChatRagRequestDto originalRequest, Principal principal) {
        long requestStartedNanos = System.nanoTime();
        ChatRequestDto chat = originalRequest.chat();
        ResolvedRagAnswerPolicy answerPolicy =
                ragAnswerPolicyResolver.resolve(originalRequest.answerMode());
        ResolvedRagSourcePolicy sourcePolicy =
                ragSourcePolicyResolver.resolve(originalRequest.sourceScope());
        ObjectScope objectScope = resolveObjectScope(originalRequest.objectType(), originalRequest.objectId());
        RagEvidenceSourceSelection indexedSelection = RagEvidenceSourceSelection.resolve(
                originalRequest.indexedWebSources(),
                indexedRagSourceProviders,
                ragObjectAuthorizationRouter,
                indexedWebMaxSources());
        originalRequest = applyIndexedSourceEmbedding(originalRequest, indexedSelection);
        RagRetrievalPolicyDto appliedPolicy = resolveRetrievalPolicy(originalRequest, objectScope);
        ChatRagRequestDto request = applyRetrievalPolicy(originalRequest, appliedPolicy);
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
        boolean hasIndexedSources = !indexedSelection.indexedSources().isEmpty();
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
            if (!hasFilter && !hasIndexedSources) {
                throw new IllegalArgumentException("ragQuery가 없으면 objectType 또는 objectId를 제공해야 합니다");
            }
            ragResults = hasFilter
                    ? ragPipelineService.listByObject(objectType, objectId, ragTopK)
                    : List.of();
            objectCandidateResults = true;
            retrievalMode = "DOCUMENT_CHUNKS";
        } else {
            String resolvedQuery = resolveRagQuery(request);
            if (hasFilter || !hasIndexedSources) {
                RagChatRetrievalService.RetrievalResult retrieval = ragChatRetrievalService.retrieve(
                        request,
                        resolvedQuery,
                        objectType,
                        objectId,
                        ragTopK,
                        minScore,
                        requestedTopK(request),
                        shouldExposeDiagnostics(request),
                        queryIntent.intent() == RagQueryIntentClassifier.Intent.DOCUMENT_METADATA);
                ragResults = retrieval.results();
                retrievalDebug = retrieval.debug();
            } else {
                ragResults = List.of();
            }
            if (queryIntent.intent() == RagQueryIntentClassifier.Intent.DOCUMENT_METADATA) {
                retrievalMode = "DOCUMENT_METADATA";
            }
        }
        ragResults = mergeIndexedWebResults(
                request,
                resolveRagQuery(request),
                ragResults,
                indexedSelection,
                ragTopK,
                minScore,
                queryIntent);
        ragResults = filterEvidenceResults(ragResults);
        if (documentOverview != null) {
            documentOverview = documentOverviewAssembler.assemble(
                    ragResults,
                    MAX_WHOLE_DOCUMENT_CONTEXT_CHARS,
                    documentOverview.fullCoverage());
        }
        if (!skipFinalResultLimit) {
            ragResults = limitRagResults(ragResults, resultTopK);
        }
        RagExternalEvidenceService.Result externalEvidence = ragExternalEvidenceService.retrieve(
                resolveRagQuery(request),
                sourcePolicy,
                request.externalSourceOptions());
        long retrievalElapsedMs = Math.max(0L, (System.nanoTime() - retrievalStartedNanos) / 1_000_000L);
        recordRetrievalPolicyUsage(appliedPolicy, request, ragResults.size(), ragResults.isEmpty(), retrievalElapsedMs);
        RagRetrievalDiagnostics diagnostics = ragPipelineService.latestDiagnostics().orElse(null);
        boolean exposeDiagnostics = shouldExposeDiagnostics(request);
        Map<String, Object> ragMetadata = new LinkedHashMap<>();
        ragMetadata.put("sourcePolicy", sourcePolicy.toMetadata());
        ragMetadata.put("indexedWebSources", indexedSelection.toMetadata());
        ragMetadata.put("externalSourceReview", externalEvidence.toMetadata());
        if (ragResults.isEmpty() && externalEvidence.evidence().isEmpty()) {
            ragMetadata.put("ragReferences", List.of());
            ragMetadata.put("ragSkippedChat", true);
            ragMetadata.put("ragSkipReason", RAG_SKIP_REASON_NO_RESULTS);
            putQueryIntentMetadata(ragMetadata, queryIntent, retrievalMode, ragTopK, minScore);
            if (exposeDiagnostics && retrievalDebug.enabled()) {
                ragMetadata.put("retrieval", sanitizePublicMetadata(retrievalDebug.toMetadata()));
            }
            putRetrievalPolicyMetadata(ragMetadata, appliedPolicy);
            return new PreparedRagChat(
                    requestStartedNanos,
                    request,
                    objectScope,
                    chat,
                    null,
                    null,
                    diagnostics,
                    exposeDiagnostics,
                    Map.copyOf(ragMetadata),
                    retrievalElapsedMs,
                    0L,
                    queryIntent,
                    retrievalMode,
                    0,
                    false,
                    true,
                    answerPolicy,
                    sourcePolicy,
                    externalEvidence,
                    PackedEvidenceSet.empty(RAG_NO_RESULTS_MESSAGE, Map.of()));
        }

        List<RagSearchResult> expansionCandidates = contextExpansionCandidates(
                ragResults,
                objectType,
                objectId,
                resultTopK,
                objectCandidateResults);
        RagContextBuilder.BuildResult contextResult = ragResults.isEmpty()
                ? new RagContextBuilder.BuildResult("", null)
                : documentOverview == null
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
        if (documentOverview != null || overviewReduction.applied()) {
            List<RagSearchResult> citationResults = documentOverview == null
                    ? contextResult.usedResults()
                    : documentOverview.references();
            RagContextBuilder.BuildResult citationContext = ragContextBuilder.buildWithDiagnostics(
                    citationResults,
                    citationResults);
            context = combineSystemPrompts(
                    context,
                    "다음은 최종 답변에서 인용할 수 있는 근거 목록입니다.\n" + citationContext.context());
            contextResult = new RagContextBuilder.BuildResult(
                    context,
                    citationContext.diagnostics(),
                    citationContext.usedResults());
        }
        PackedEvidenceSet evidenceSet =
                contextResult.evidenceSet().withExternalEvidence(externalEvidence.evidence());
        RagEvidenceCoverageRequirement coverageRequirement =
                RagEvidenceCoverageRequirement.classify(resolveRagQuery(request));
        evidenceSet = evidenceSet.withDiagnostic(
                "coverageRequirement", coverageRequirement.name());
        context = evidenceSet.promptContext();
        contextResult = new RagContextBuilder.BuildResult(
                context,
                contextResult.diagnostics(),
                contextResult.usedResults(),
                evidenceSet);
        boolean insufficientCoverage =
                coverageRequirement == RagEvidenceCoverageRequirement.DOCUMENT_AND_EXTERNAL
                        && (!contextResult.evidenceSet().hasOrigin("DOCUMENT")
                                || !contextResult.evidenceSet().hasExternalOrigin());
        if (contextResult.evidenceSet().evidence().isEmpty() || insufficientCoverage) {
            ragMetadata.put("ragReferences", List.of());
            ragMetadata.put("ragSkippedChat", true);
            ragMetadata.put(
                    "ragSkipReason",
                    insufficientCoverage
                            ? RAG_SKIP_REASON_INSUFFICIENT_SOURCE_COVERAGE
                            : RAG_SKIP_REASON_NO_PACKED_EVIDENCE);
            ragMetadata.put("coverageRequirement", coverageRequirement.name());
            putQueryIntentMetadata(ragMetadata, queryIntent, retrievalMode, ragTopK, minScore);
            putRetrievalPolicyMetadata(ragMetadata, appliedPolicy);
            return new PreparedRagChat(
                    requestStartedNanos,
                    request,
                    objectScope,
                    chat,
                    null,
                    null,
                    diagnostics,
                    exposeDiagnostics,
                    Map.copyOf(ragMetadata),
                    retrievalElapsedMs,
                    overviewReductionElapsedMs,
                    queryIntent,
                    retrievalMode,
                    ragResults.size(),
                    overviewReduction.cacheHit(),
                    true,
                    answerPolicy,
                    sourcePolicy,
                    externalEvidence,
                    contextResult.evidenceSet());
        }

        List<ChatMessageDto> augmentedMessages = new ArrayList<>();
        augmentedMessages.add(new ChatMessageDto(
                "system",
                combineRagSystemPrompts(
                        context,
                        chat.systemPrompt(),
                        queryIntent,
                        answerPolicy,
                        sourcePolicy)));
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
                chat.memory(),
                chat.deploymentId());

        if (exposeDiagnostics && contextResult.diagnostics() != null) {
            ragMetadata.put("ragContextDiagnostics",
                    sanitizePublicMetadata(contextResult.diagnostics().toMetadata()));
        }
        if (exposeDiagnostics && retrievalDebug.enabled()) {
            ragMetadata.put("retrieval", sanitizePublicMetadata(retrievalDebug.toMetadata()));
        }
        putQueryIntentMetadata(ragMetadata, queryIntent, retrievalMode, ragTopK, minScore);
        if (documentOverview != null) {
            ragMetadata.put("overviewSourceChunkCount", documentOverview.sourceChunkCount());
            ragMetadata.put("overviewCoverageStatus", documentOverview.fullCoverage() ? "FULL" : "PARTIAL");
        }
        if (overviewReduction.applied()) {
            ragMetadata.put("overviewReduction", "MAP_REDUCE");
            ragMetadata.put("overviewReductionSegmentCount", overviewReduction.segmentCount());
            ragMetadata.put("overviewReductionCacheHit", overviewReduction.cacheHit());
        }
        putRetrievalPolicyMetadata(ragMetadata, appliedPolicy);
        return new PreparedRagChat(
                requestStartedNanos,
                request,
                objectScope,
                chat,
                augmented,
                memory,
                diagnostics,
                exposeDiagnostics,
                Map.copyOf(ragMetadata),
                retrievalElapsedMs,
                overviewReductionElapsedMs,
                queryIntent,
                retrievalMode,
                ragResults.size(),
                overviewReduction.cacheHit(),
                false,
                answerPolicy,
                sourcePolicy,
                externalEvidence,
                contextResult.evidenceSet());
    }

    private Map<String, Object> withRagTiming(PreparedRagChat prepared, long generationElapsedMs) {
        Map<String, Object> metadata = new LinkedHashMap<>(prepared.ragMetadata());
        metadata.put("answerPolicy", prepared.answerPolicy().toMetadata());
        metadata.put("sourcePolicy", prepared.sourcePolicy().toMetadata());
        metadata.put("ragTiming", Map.of(
                "retrievalMs", prepared.retrievalElapsedMs(),
                "overviewReductionMs", prepared.overviewReductionElapsedMs(),
                "generationMs", generationElapsedMs,
                "totalMs", elapsedMillis(prepared.requestStartedNanos())));
        return metadata;
    }

    private Map<String, Object> skippedRagMetadata(PreparedRagChat prepared) {
        Map<String, Object> metadata = withRagTiming(prepared, 0L);
        RagAnswerOutcome outcome = skippedOutcome(prepared);
        metadata.put("canonicalContent", skippedMessage(outcome));
        metadata.put("citationValidationStatus",
                RagCitationValidator.Status.NO_PACKED_EVIDENCE.name());
        metadata.putAll(ragOutcomeMetadata(prepared, outcome));
        return metadata;
    }

    private void logSlowRagChat(PreparedRagChat prepared, long generationElapsedMs) {
        long totalElapsedMs = elapsedMillis(prepared.requestStartedNanos());
        if (totalElapsedMs < 10_000L) {
            return;
        }
        log.info("Slow RAG chat: intent={}, mode={}, results={}, retrievalMs={}, overviewReductionMs={}, "
                        + "generationMs={}, totalMs={}, overviewCacheHit={}",
                prepared.queryIntent().intent(),
                prepared.retrievalMode(),
                prepared.resultCount(),
                prepared.retrievalElapsedMs(),
                prepared.overviewReductionElapsedMs(),
                generationElapsedMs,
                totalElapsedMs,
                prepared.overviewCacheHit());
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
        ConversationDetailDto detail =
                conversationChatService.detail(conversationChatService.ownerId(principal), conversationId);
        return ResponseEntity.ok(ApiResponse.ok(redactConversationDetail(detail)));
    }

    private ConversationDetailDto redactConversationDetail(ConversationDetailDto detail) {
        List<ConversationMessageDto> messages = detail.messages().stream()
                .map(this::redactConversationMessage)
                .toList();
        return new ConversationDetailDto(
                detail.conversationId(),
                detail.title(),
                detail.summary(),
                detail.status(),
                detail.parentConversationId(),
                detail.forkedFromMessageId(),
                detail.messageCount(),
                detail.createdAt(),
                detail.lastUpdatedAt(),
                sanitizePublicMetadata(detail.metadata()),
                messages);
    }

    private ConversationMessageDto redactConversationMessage(ConversationMessageDto message) {
        Map<String, Object> stored = message.metadata() == null ? Map.of() : message.metadata();
        boolean ragTurn = Boolean.TRUE.equals(stored.get("ragTurn"));
        boolean canReadEvidence = !ragTurn || canReadStoredRagScope(stored);
        Map<String, Object> projected = new LinkedHashMap<>(stored);
        if (!canReadEvidence) {
            projected = new LinkedHashMap<>();
            projected.put("ragTurn", true);
            projected.put("ragReferences", List.of());
            projected.put("ragReferencesRedacted", true);
        }
        return new ConversationMessageDto(
                message.messageId(),
                message.role(),
                canReadEvidence ? message.content() : "이 근거 기반 응답을 볼 권한이 없습니다.",
                message.createdAt(),
                sanitizePublicMetadata(projected));
    }

    private boolean canReadStoredRagScope(Map<String, Object> metadata) {
        return canReadStoredRagScope(metadata, ragObjectAuthorizationRouter);
    }

    static boolean canReadStoredRagScope(
            Map<String, Object> metadata,
            RagObjectAuthorizationRouter authorizationRouter) {
        if (metadata == null
                || authorizationRouter == null
                || !authorizationRouter.canReadRagService()) {
            return false;
        }
        String objectType = normalizeText(Objects.toString(metadata.get("ragObjectType"), null));
        String objectId = normalizeText(Objects.toString(metadata.get("ragObjectId"), null));
        if ((objectType == null) != (objectId == null)) {
            return false;
        }
        if (objectType != null && !authorizationRouter.canRead(objectType, objectId)) {
            return false;
        }

        Object indexedValue = metadata.get("indexedWebSources");
        if (indexedValue == null) {
            return true;
        }
        if (!(indexedValue instanceof Map<?, ?> indexedMetadata)) {
            return false;
        }
        Object sourcesValue = indexedMetadata.get("sources");
        if (!(sourcesValue instanceof List<?> sources)) {
            return false;
        }
        Object countValue = indexedMetadata.get("count");
        if (countValue instanceof Number count && count.intValue() != sources.size()) {
            return false;
        }
        for (Object sourceValue : sources) {
            if (!(sourceValue instanceof Map<?, ?> source)) {
                return false;
            }
            String sourceId = normalizeText(Objects.toString(source.get("sourceId"), null));
            if (sourceId == null || !authorizationRouter.canRead("web_source", sourceId)) {
                return false;
            }
        }
        return true;
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
        if (Boolean.TRUE.equals(conversationChatService
                .lastAssistantMetadata(ownerId, request.conversationId())
                .get("ragTurn"))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "RAG_REGENERATION_REQUIRES_RAG_ENDPOINT");
        }
        List<ChatMessage> messages = conversationChatService.messagesForRegenerate(ownerId, request.conversationId()).stream()
                .map(studio.one.platform.ai.core.chat.ChatConversationMessage::message)
                .toList();
        ChatRequestDto chat = request.chat();
        String provider = chat == null ? null : deploymentOrProvider(chat);
        ChatRequest domainRequest = toDomainChatRequest(chat == null ? minimalChatRequest(messages) : chat, messages);
        ChatResponse response = chatPort(provider).chat(domainRequest);
        int messageCount = conversationChatService.replaceLastAssistantResponse(ownerId, request.conversationId(), response);
        return ResponseEntity.ok(ApiResponse.ok(toDto(response, null, false,
                conversationMetadata(request.conversationId(), messageCount))));
    }

    @PostMapping("/rag/regenerate")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','write') "
            + "and @endpointAuthz.can('services:ai_rag','read') "
            + "and @ragObjectAuthorizationRouter.canRead(#request.rag)")
    public ResponseEntity<ApiResponse<ChatResponseDto>> regenerateWithRag(
            @Valid @RequestBody RagRegenerateRequestDto request,
            Principal principal) {
        String ownerId = conversationChatService.ownerId(principal);
        Map<String, Object> storedMetadata =
                conversationChatService.lastAssistantMetadata(ownerId, request.conversationId());
        if (!Boolean.TRUE.equals(storedMetadata.get("ragTurn"))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "RAG_REGENERATION_REQUIRES_RAG_TURN");
        }
        List<ChatMessageDto> messages = toDtoMessages(conversationChatService
                .messagesForRegenerate(ownerId, request.conversationId()).stream()
                .map(studio.one.platform.ai.core.chat.ChatConversationMessage::message)
                .toList());
        ChatRagRequestDto rag = request.rag();
        String storedObjectType = metadataText(storedMetadata, "ragObjectType");
        String storedObjectId = metadataText(storedMetadata, "ragObjectId");
        if (!Objects.equals(storedObjectType, normalizeText(rag.objectType()))
                || !Objects.equals(storedObjectId, normalizeText(rag.objectId()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "RAG_REGENERATION_SCOPE_MISMATCH");
        }
        String storedAnswerMode = nestedMetadataText(storedMetadata, "answerPolicy", "requestedMode");
        if (storedAnswerMode == null) {
            storedAnswerMode = nestedMetadataText(storedMetadata, "answerPolicy", "effectiveMode");
        }
        String storedSourceScope = replaySourceScope(storedMetadata, rag.sourceScope());
        ChatRequestDto settings = rag.chat();
        ChatRequestDto replayChat = new ChatRequestDto(
                settings.provider(),
                settings.systemPrompt(),
                messages,
                settings.model(),
                settings.temperature(),
                settings.topP(),
                settings.topK(),
                settings.maxOutputTokens(),
                settings.stopSequences(),
                null,
                settings.deploymentId());
        ChatRagRequestDto replay = new ChatRagRequestDto(
                replayChat,
                messages.get(messages.size() - 1).content(),
                rag.ragTopK(),
                rag.objectType(),
                rag.objectId(),
                rag.embeddingProfileId(),
                rag.embeddingProvider(),
                rag.embeddingModel(),
                rag.topK(),
                rag.minScore(),
                rag.debug(),
                rag.retrievalStrategy(),
                rag.retrievalOptions(),
                rag.embeddingDeploymentId(),
                storedAnswerMode,
                storedSourceScope,
                rag.externalSourceOptions(),
                rag.indexedWebSources());
        return chatWithRagInternal(replay, principal, request.conversationId());
    }

    static String replaySourceScope(Map<String, Object> storedMetadata, String requestedSourceScope) {
        String storedSourceScope = nestedMetadataText(storedMetadata, "sourcePolicy", "requestedScope");
        if (storedSourceScope == null) {
            storedSourceScope = nestedMetadataText(storedMetadata, "sourcePolicy", "effectiveScope");
        }
        if (storedSourceScope == null) {
            return requestedSourceScope;
        }
        RagSourceScope stored = RagSourceScope.parse(storedSourceScope);
        RagSourceScope requested = RagSourceScope.parse(requestedSourceScope);
        if (requested != null && requested != stored) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "RAG_REGENERATION_SOURCE_SCOPE_MISMATCH");
        }
        return stored.name();
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

    private String deploymentOrProvider(ChatRequestDto request) {
        return request.deploymentId() == null || request.deploymentId().isBlank()
                ? request.provider()
                : request.deploymentId();
    }

    private ChatResponse executeChat(ChatPort port, ChatRequest request) {
        return executeChat(port, request, AiModelUsageRequestKind.UNKNOWN);
    }

    private ChatResponse executeChat(
            ChatPort port,
            ChatRequest request,
            AiModelUsageRequestKind requestKind) {
        try {
            ChatResponse response = port.chat(request);
            AiModelUsageStore.UsageEstimate estimate = modelUsageStore.record(
                    response.typedMetadata(), response.model(), requestKind);
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
            String deployment = deploymentOrProvider(chat);
            ChatPort port = chatPort(deployment);
            return documentMapReduceOverview.reduce(
                    objectType,
                    objectId,
                    deployment,
                    chat.model(),
                    context,
                    MAP_REDUCE_OVERVIEW_THRESHOLD_CHARS,
                    MAP_REDUCE_SEGMENT_CHARS,
                    prompt -> segmentSummary(port, chat, prompt));
        } catch (RuntimeException ex) {
            log.warn("Large document map-reduce overview failed; using the original context: errorType={}",
                    ex.getClass().getSimpleName());
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

    private ChatRagRequestDto applyIndexedSourceEmbedding(
            ChatRagRequestDto request,
            RagEvidenceSourceSelection selection) {
        if (selection.indexedSources().isEmpty()) {
            return request;
        }
        String requestedDeployment = normalizeText(request.embeddingDeploymentId());
        if (requestedDeployment != null
                && !requestedDeployment.equals(selection.embeddingDeploymentId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "WEB_SOURCE_EMBEDDING_SPACE_MISMATCH");
        }
        if (requestedDeployment == null
                && (normalizeText(request.embeddingProfileId()) != null
                        || normalizeText(request.embeddingProvider()) != null
                        || normalizeText(request.embeddingModel()) != null)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "WEB_SOURCE_EMBEDDING_SPACE_MISMATCH");
        }
        if (requestedDeployment != null) {
            return request;
        }
        return new ChatRagRequestDto(
                request.chat(),
                request.ragQuery(),
                request.ragTopK(),
                request.objectType(),
                request.objectId(),
                null,
                null,
                null,
                request.topK(),
                request.minScore(),
                request.debug(),
                request.retrievalStrategy(),
                request.retrievalOptions(),
                selection.embeddingDeploymentId(),
                request.answerMode(),
                request.sourceScope(),
                request.externalSourceOptions(),
                request.indexedWebSources());
    }

    private List<RagSearchResult> mergeIndexedWebResults(
            ChatRagRequestDto request,
            String query,
            List<RagSearchResult> baseResults,
            RagEvidenceSourceSelection selection,
            int ragTopK,
            double minScore,
            RagQueryIntentClassifier.Classification queryIntent) {
        if (selection.indexedSources().isEmpty()) {
            return baseResults;
        }
        List<RagSearchResult> merged = new ArrayList<>(
                baseResults == null ? List.of() : baseResults);
        for (var source : selection.indexedSources()) {
            int sourceCandidateLimit = Math.min(
                    ragContextMaxCandidates,
                    Math.max(ragTopK, ragTopK * DEFAULT_CONTEXT_EXPANSION_CANDIDATE_MULTIPLIER));
            List<RagSearchResult> sourceResults;
            if (query == null || query.isBlank()) {
                sourceResults = ragPipelineService.listByObject(
                                source.objectType(), source.objectId(), sourceCandidateLimit)
                        .stream()
                        .filter(result -> source.partitionIds().isEmpty()
                                || source.partitionIds().contains(metadataText(result.metadata(), "partitionId")))
                        .toList();
            } else {
                sourceResults = ragChatRetrievalService.retrieve(
                                request,
                                query,
                                source.objectType(),
                                source.objectId(),
                                sourceCandidateLimit,
                                minScore,
                                requestedTopK(request),
                                shouldExposeDiagnostics(request),
                                queryIntent.intent() == RagQueryIntentClassifier.Intent.DOCUMENT_METADATA,
                                source.partitionIds())
                        .results();
            }
            if (source.partitionIds().size() > 1) {
                sourceResults = capIndexedWebCandidatesPerPage(
                        sourceResults, INDEXED_WEB_PAGE_CANDIDATE_CAP, sourceCandidateLimit);
            }
            for (RagSearchResult result : sourceResults) {
                if (!belongsToIndexedSource(result, source)) {
                    continue;
                }
                Map<String, Object> metadata = new LinkedHashMap<>(result.metadata());
                metadata.put("evidenceOrigin", "INDEXED_WEB");
                metadata.put("sourceRevisionId", source.revisionId());
                source.metadata().forEach(metadata::putIfAbsent);
                merged.add(new RagSearchResult(
                        result.documentId(), result.content(), metadata, result.score()));
            }
        }
        Map<String, RagSearchResult> deduplicated = new LinkedHashMap<>();
        merged.stream()
                .sorted(Comparator.comparingDouble(RagSearchResult::score).reversed())
                .forEach(result -> deduplicated.putIfAbsent(indexedEvidenceKey(result), result));
        return deduplicated.values().stream()
                .limit(ragContextMaxCandidates)
                .toList();
    }

    static boolean belongsToIndexedSource(
            RagSearchResult result,
            studio.one.platform.ai.core.rag.indexed.ResolvedIndexedRagSource source) {
        if (source.partitionIds().isEmpty()) {
            String revision = metadataText(result.metadata(), "sourceRevisionId");
            return revision == null || source.revisionId().equals(revision);
        }
        String partition = metadataText(result.metadata(), "partitionId");
        if (partition == null) {
            partition = metadataText(result.metadata(), "pageRevisionId");
        }
        if (partition == null) {
            partition = metadataText(result.metadata(), "sourceRevisionId");
        }
        return partition != null && source.partitionIds().contains(partition);
    }

    static List<RagSearchResult> capIndexedWebCandidatesPerPage(
            List<RagSearchResult> candidates,
            int perPageLimit,
            int totalLimit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> pageCounts = new HashMap<>();
        List<RagSearchResult> accepted = new ArrayList<>();
        for (RagSearchResult candidate : candidates.stream()
                .sorted(Comparator.comparingDouble(RagSearchResult::score).reversed())
                .toList()) {
            String pageKey = metadataText(candidate.metadata(), "pageRevisionId");
            if (pageKey == null) {
                pageKey = metadataText(candidate.metadata(), "partitionId");
            }
            if (pageKey == null) {
                pageKey = "unpartitioned:" + indexedEvidenceKey(candidate);
            }
            int count = pageCounts.getOrDefault(pageKey, 0);
            if (count >= Math.max(1, perPageLimit)) {
                continue;
            }
            pageCounts.put(pageKey, count + 1);
            accepted.add(candidate);
            if (accepted.size() >= Math.max(1, totalLimit)) {
                break;
            }
        }
        return List.copyOf(accepted);
    }

    private static String indexedEvidenceKey(RagSearchResult result) {
        return String.join("|",
                normalizeText(metadataText(result.metadata(), "evidenceOrigin")) == null
                        ? "DOCUMENT"
                        : metadataText(result.metadata(), "evidenceOrigin"),
                normalizeText(metadataText(result.metadata(), "objectId")) == null
                        ? ""
                        : metadataText(result.metadata(), "objectId"),
                normalizeText(metadataText(result.metadata(), "sourceRevisionId")) == null
                        ? ""
                        : metadataText(result.metadata(), "sourceRevisionId"),
                normalizeText(metadataText(result.metadata(), "chunkId")) == null
                        ? result.documentId() + ":" + Integer.toHexString(result.content().hashCode())
                        : metadataText(result.metadata(), "chunkId"));
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
                effectiveOptions,
                request.embeddingDeploymentId(),
                request.answerMode(),
                request.sourceScope(),
                request.externalSourceOptions(),
                request.indexedWebSources());
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
                adjustedOptions,
                request.embeddingDeploymentId(),
                request.answerMode(),
                request.sourceScope(),
                request.externalSourceOptions(),
                request.indexedWebSources());
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
            log.warn("Failed to record RAG retrieval policy usage: objectType={}, errorType={}",
                    policy.objectType(), ex.getClass().getSimpleName());
        }
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : normalizeText(value.toString());
    }

    private static String nestedMetadataText(
            Map<String, Object> metadata,
            String containerKey,
            String valueKey) {
        Object container = metadata == null ? null : metadata.get(containerKey);
        if (!(container instanceof Map<?, ?> values)) {
            return null;
        }
        Object value = values.get(valueKey);
        return value == null ? null : normalizeText(value.toString());
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
            RagQueryIntentClassifier.Classification classification,
            ResolvedRagAnswerPolicy answerPolicy,
            ResolvedRagSourcePolicy sourcePolicy) {
        return ragAnswerPromptComposer.compose(
                context,
                clientPrompt,
                classification,
                answerPolicy,
                sourcePolicy,
                INTERPRETIVE_ANALYSIS_PROMPT,
                DOCUMENT_SUMMARY_PROMPT);
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

    private RagAnswerFinalizer.FinalizedAnswer finalizeRagResponse(
            ChatResponse response,
            PackedEvidenceSet evidenceSet,
            ResolvedRagAnswerPolicy answerPolicy,
            RagQueryIntentClassifier.Classification classification) {
        String draft = response.messages().stream()
                .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                .map(ChatMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse("");
        RagAnswerFinalizer.FinalizedAnswer finalized =
                ragAnswerFinalizer.finalizeAnswer(draft, evidenceSet, answerPolicy, classification);
        if (log.isDebugEnabled()) {
            RagAnswerOutcome outcome = finalized.outcome();
            log.debug(
                    "RAG answer finalization type={}, stage={}, reason={}, packedEvidenceCount={}, "
                            + "validationUnitCount={}, citedValidationUnitCount={}",
                    outcome.type(),
                    outcome.stage(),
                    outcome.reasonCode(),
                    outcome.packedEvidenceCount(),
                    outcome.validationUnitCount(),
                    outcome.citedValidationUnitCount());
        }
        return finalized;
    }

    private ChatResponse canonicalResponse(ChatResponse response, String canonicalContent) {
        return new ChatResponse(
                List.of(ChatMessage.assistant(canonicalContent)),
                response.model(),
                response.metadata());
    }

    private ChatResponse responseWithMetadata(ChatResponse response, Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>(response.metadata());
        metadata.putAll(extraMetadata);
        return new ChatResponse(response.messages(), response.model(), Map.copyOf(metadata));
    }

    private Map<String, Object> persistRagConversation(
            Principal principal,
            PreparedRagChat prepared,
            ChatResponse response,
            String replaceConversationId) {
        if (replaceConversationId != null && !replaceConversationId.isBlank()) {
            String ownerId = conversationChatService.ownerId(principal);
            int messageCount = conversationChatService.replaceLastAssistantResponse(
                    ownerId, replaceConversationId, response);
            return conversationMetadata(replaceConversationId, messageCount);
        }
        int memoryMessageCount = appendMemory(
                prepared.memory(), prepared.chat().messages(), response);
        appendConversation(
                principal,
                prepared.memory(),
                prepared.chat().messages().stream().map(this::toDomainMessage).toList(),
                response);
        return memoryMetadata(prepared.memory(), memoryMessageCount);
    }

    private Map<String, Object> ragTurnMetadata(
            PreparedRagChat prepared,
            String canonicalContent,
            String citationValidationStatus,
            String policyValidationStatus,
            String cacheStatus,
            RagAnswerOutcome outcome) {
        Map<String, Object> metadata = new LinkedHashMap<>(prepared.ragMetadata());
        metadata.put("answerPolicy", prepared.answerPolicy().toMetadata());
        metadata.put("sourcePolicy", prepared.sourcePolicy().toMetadata());
        metadata.put("externalSourceReview", prepared.externalEvidence().toMetadata());
        metadata.put("ragTurn", true);
        metadata.put("canonicalContent", canonicalContent);
        metadata.put("citationValidationStatus", citationValidationStatus);
        metadata.put("answerPolicyValidationStatus", policyValidationStatus);
        metadata.put("ragAnswerCache", cacheStatus);
        metadata.putAll(ragOutcomeMetadata(prepared, outcome));
        if (prepared.objectScope() != null && prepared.objectScope().hasFilter()) {
            metadata.put("ragObjectType", prepared.objectScope().objectType());
            metadata.put("ragObjectId", prepared.objectScope().objectId());
        }
        return Map.copyOf(metadata);
    }

    private Map<String, Object> ragOutcomeMetadata(
            PreparedRagChat prepared,
            RagAnswerOutcome outcome) {
        RagAnswerOutcome counted = new RagAnswerOutcome(
                outcome.type(),
                outcome.stage(),
                outcome.reasonCode(),
                rawRetrievalCount(prepared),
                prepared.resultCount(),
                prepared.evidenceSet().evidence().size(),
                outcome.usedEvidenceIndexes(),
                outcome.citationValidationStatus(),
                outcome.policyValidationStatus(),
                outcome.validationUnitCount(),
                outcome.citedValidationUnitCount(),
                outcome.partial(),
                outcome.originalValidationUnitCount(),
                outcome.omittedValidationUnitCount());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ragAnswerOutcome", counted.toMetadata());
        String usageStatus = counted.type() == RagAnswerOutcome.Type.ANSWERED
                ? "CITED"
                : "RETRIEVED_ONLY";
        metadata.put("ragReferences", prepared.evidenceSet().toPublicReferences(
                counted.usedEvidenceIndexes(), usageStatus));
        metadata.put("evidenceSourceSelection", evidenceSourceSelectionMetadata(prepared, counted));
        return Map.copyOf(metadata);
    }

    private Map<String, Object> evidenceSourceSelectionMetadata(
            PreparedRagChat prepared,
            RagAnswerOutcome outcome) {
        Set<String> packedOrigins = prepared.evidenceSet().evidence().stream()
                .map(PackedEvidenceSet.PackedEvidence::origin)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Set<String> usedOrigins = prepared.evidenceSet().evidence().stream()
                .filter(item -> outcome.usedEvidenceIndexes().contains(item.citationIndex()))
                .map(PackedEvidenceSet.PackedEvidence::origin)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        int indexedWebSourceCount = 0;
        Object indexedMetadata = prepared.ragMetadata().get("indexedWebSources");
        if (indexedMetadata instanceof Map<?, ?> values && values.get("count") instanceof Number count) {
            indexedWebSourceCount = Math.max(0, count.intValue());
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentScopeSelected", prepared.objectScope() != null
                && prepared.objectScope().hasFilter()
                && !"web_source".equalsIgnoreCase(prepared.objectScope().objectType()));
        metadata.put("indexedWebSourceCount", indexedWebSourceCount);
        metadata.put("officialExternalEnabled", prepared.sourcePolicy().externalSourcesEnabled());
        metadata.put("packedOrigins", List.copyOf(packedOrigins));
        metadata.put("usedOrigins", List.copyOf(usedOrigins));
        return Map.copyOf(metadata);
    }

    private int rawRetrievalCount(PreparedRagChat prepared) {
        return prepared.diagnostics() == null
                ? prepared.resultCount()
                : Math.max(prepared.resultCount(), prepared.diagnostics().beforeMinScoreCount());
    }

    private RagAnswerOutcome skippedOutcome(PreparedRagChat prepared) {
        boolean noResults = RAG_SKIP_REASON_NO_RESULTS.equals(prepared.ragMetadata().get("ragSkipReason"));
        boolean insufficientCoverage = RAG_SKIP_REASON_INSUFFICIENT_SOURCE_COVERAGE.equals(
                prepared.ragMetadata().get("ragSkipReason"));
        return new RagAnswerOutcome(
                insufficientCoverage
                        ? RagAnswerOutcome.Type.EVIDENCE_ONLY
                        : RagAnswerOutcome.Type.ABSTAINED,
                noResults ? RagAnswerOutcome.Stage.RETRIEVAL : RagAnswerOutcome.Stage.PACKING,
                insufficientCoverage
                        ? RagAnswerOutcome.ReasonCode.INSUFFICIENT_SOURCE_COVERAGE
                        : noResults
                                ? RagAnswerOutcome.ReasonCode.NO_RETRIEVAL_RESULTS
                                : RagAnswerOutcome.ReasonCode.NO_PACKED_EVIDENCE,
                rawRetrievalCount(prepared),
                prepared.resultCount(),
                prepared.evidenceSet().evidence().size(),
                Set.of(),
                RagCitationValidator.Status.NO_PACKED_EVIDENCE.name(),
                RagAnswerPolicyValidator.Status.NO_PACKED_EVIDENCE.name(),
                0,
                0);
    }

    private String skippedMessage(RagAnswerOutcome outcome) {
        if (outcome.reasonCode() == RagAnswerOutcome.ReasonCode.NO_RETRIEVAL_RESULTS) {
            return RAG_NO_RESULTS_MESSAGE;
        }
        if (outcome.reasonCode() == RagAnswerOutcome.ReasonCode.INSUFFICIENT_SOURCE_COVERAGE) {
            return RAG_INSUFFICIENT_SOURCE_COVERAGE_MESSAGE;
        }
        return RAG_NO_PACKED_EVIDENCE_MESSAGE;
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
            metadata.put("ragRetrievalSummary", sanitizePublicMetadata(diagnostics.toMetadata()));
        }
        if (exposeDiagnostics && diagnostics != null) {
            metadata.put("ragDiagnostics", sanitizePublicMetadata(diagnostics.toMetadata()));
        }
        return new ChatResponseDto(messages, response.model(), sanitizePublicMetadata(metadata));
    }

    private Map<String, Object> sanitizePublicMetadata(Map<String, ?> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (source == null) {
            return Map.of();
        }
        source.forEach((key, value) -> {
            if (key != null && value != null && !blockedPublicMetadataKey(key)) {
                sanitized.put(key, sanitizePublicMetadataValue(value));
            }
        });
        return Map.copyOf(sanitized);
    }

    private Object sanitizePublicMetadataValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, entry) -> {
                if (key != null && entry != null && !blockedPublicMetadataKey(key.toString())) {
                    typed.put(key.toString(), sanitizePublicMetadataValue(entry));
                }
            });
            return Map.copyOf(typed);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            iterable.forEach(item -> {
                if (item != null) {
                    sanitized.add(sanitizePublicMetadataValue(item));
                }
            });
            return List.copyOf(sanitized);
        }
        return value;
    }

    private boolean blockedPublicMetadataKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("objectid")
                || normalized.equals("ragobjectid")
                || normalized.equals("ragobjecttype")
                || normalized.equals("documentid")
                || normalized.equals("revisionid")
                || normalized.equals("chunkid")
                || normalized.equals("sourceref")
                || normalized.contains("fingerprint")
                || normalized.equals("query")
                || normalized.equals("content")
                || normalized.equals("text")
                || normalized.equals("principal")
                || normalized.equals("providerurl")
                || normalized.equals("baseurl");
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

    private List<RagSearchResult> filterEvidenceResults(List<RagSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .filter(result -> !isBoilerplateEvidence(result))
                .toList();
    }

    private boolean isBoilerplateEvidence(RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String label = firstText(metadata,
                "section", VectorRecord.KEY_HEADING_PATH, "headingPath", "heading", "title");
        if (label == null) {
            return false;
        }
        String normalized = label.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s·:_\\-]+", "");
        return normalized.equals("판권")
                || normalized.equals("저작권")
                || normalized.equals("저작권정보")
                || normalized.equals("이미지저작권")
                || normalized.equals("사진저작권")
                || normalized.equals("출판정보")
                || normalized.equals("도서정보")
                || normalized.equals("copyright")
                || normalized.equals("copyrightnotice")
                || normalized.equals("colophon")
                || normalized.equals("imprint")
                || normalized.equals("isbn");
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
        } catch (RuntimeException ex) {
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
            log.warn("RAG context expansion candidate fetch failed: objectType={}, errorType={}",
                    objectType, ex.getClass().getSimpleName());
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
            modelUsageStore.record(last.metadata(), last.model(), AiModelUsageRequestKind.CHAT);
        }
    }

    private void writeSkippedRagStream(
            OutputStream outputStream,
            String requestId,
            PreparedRagChat prepared) throws IOException {
        String model = normalizeText(prepared.chat().model());
        Map<String, Object> values = skippedRagMetadata(prepared);
        ChatResponseMetadata metadata = ChatResponseMetadata.from(values);
        writeSse(outputStream, requestId, ChatStreamEvent.complete(model, metadata));
    }

    private void writeRagStreamRequest(
            OutputStream outputStream,
            String requestId,
            ChatRagRequestDto request,
            Principal principal) throws IOException {
        writeRagStatus(outputStream, requestId, "retrieval_started", Map.of());
        try {
            ResolvedRagSourcePolicy requestedSourcePolicy =
                    ragSourcePolicyResolver.resolve(request.sourceScope());
            if (requestedSourcePolicy.externalSourcesEnabled()) {
                writeRagStatus(outputStream, requestId, "external_search", Map.of(
                        "scope", requestedSourcePolicy.effectiveScope().name()));
            }
            PreparedRagChat prepared = prepareRagChat(request, principal);
            writeRagStatus(outputStream, requestId, "retrieval_complete", Map.of(
                    "retrievalMs", prepared.retrievalElapsedMs(),
                    "resultCount", prepared.resultCount(),
                    "externalStatus", prepared.externalEvidence().status().name(),
                    "externalEvidenceCount", prepared.externalEvidence().evidence().size()));
            if (prepared.skippedChat()) {
                writeSkippedRagStream(outputStream, requestId, prepared);
                return;
            }

            Optional<CachedRagHit> cached = cachedRagAnswer(prepared, principal);
            if (cached.isPresent()) {
                writeCachedRagStream(outputStream, requestId, prepared, principal, cached.get().answer());
                return;
            }

            ChatPort port = chatPort(deploymentOrProvider(prepared.chat()));
            writeRagStatus(outputStream, requestId, "generation_started", Map.of());
            long generationStartedNanos = System.nanoTime();
            java.util.stream.Stream<ChatStreamEvent> events =
                    openStream(port, toDomainChatRequest(prepared.augmentedChat()));
            writeRagStreamEvents(
                    outputStream,
                    requestId,
                    events,
                    prepared,
                    principal,
                    generationStartedNanos);
        } catch (RuntimeException ex) {
            log.warn("RAG stream request failed: requestId={}, errorType={}",
                    requestId, ex.getClass().getSimpleName());
            writeSse(outputStream, requestId,
                    ragStreamError("RAG_STREAM_FAILED"));
        }
    }

    private void writeRagStatus(
            OutputStream outputStream,
            String requestId,
            String stage,
            Map<String, Object> details) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>(details);
        payload.put("type", "rag_status");
        payload.put("stage", stage);
        payload.put("requestId", requestId);
        String serialized = "event: rag_status\n"
                + "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        outputStream.write(serialized.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void writeRagStreamEvents(
            OutputStream outputStream,
            String requestId,
            java.util.stream.Stream<ChatStreamEvent> events,
            PreparedRagChat prepared,
            Principal principal,
            long generationStartedNanos) throws IOException {
        StringBuilder assistant = new StringBuilder();
        ChatStreamEvent last = null;
        try (events) {
            Iterator<ChatStreamEvent> iterator = events.iterator();
            while (iterator.hasNext()) {
                ChatStreamEvent event = iterator.next();
                last = event;
                if (event.type() == ChatStreamEventType.ERROR) {
                    writeSse(outputStream, requestId, ragStreamError("PROVIDER_STREAM_FAILED"));
                    return;
                }
                if (event.type() == ChatStreamEventType.DELTA) {
                    assistant.append(event.delta());
                    continue;
                }
                if (event.type() == ChatStreamEventType.COMPLETE) {
                    ChatStreamEvent completed = completeRagStream(
                            prepared,
                            principal,
                            assistant,
                            event,
                            elapsedMillis(generationStartedNanos));
                    writeSse(outputStream, requestId, completed);
                    return;
                }
                // RAG streams expose progress only until the canonical complete event.
            }
        } catch (RuntimeException ex) {
            log.warn("RAG stream generation failed: requestId={}, errorType={}",
                    requestId, ex.getClass().getSimpleName());
            writeSse(outputStream, requestId, ragStreamError("RAG_GENERATION_FAILED"));
            return;
        }
        if (assistant.length() > 0) {
            ChatStreamEvent completed = completeRagStream(
                    prepared,
                    principal,
                    assistant,
                    last,
                    elapsedMillis(generationStartedNanos));
            writeSse(outputStream, requestId, completed);
        }
    }

    private ChatStreamEvent completeRagStream(
            PreparedRagChat prepared,
            Principal principal,
            StringBuilder assistant,
            ChatStreamEvent terminal,
            long generationElapsedMs) {
        String model = terminal == null || terminal.model().isBlank()
                ? normalizeText(prepared.chat().model())
                : terminal.model();
        ChatResponse response = new ChatResponse(
                List.of(ChatMessage.assistant(assistant.toString())),
                model,
                terminal == null ? Map.of() : terminal.metadata().toMap());
        RagAnswerFinalizer.FinalizedAnswer finalized =
                ragAnswerFinalizer.finalizeAnswer(
                        assistant.toString(),
                        prepared.evidenceSet(),
                        prepared.answerPolicy(),
                        prepared.queryIntent());
        response = canonicalResponse(response, finalized.canonicalContent());
        response = responseWithMetadata(response, ragTurnMetadata(
                prepared,
                finalized.canonicalContent(),
                finalized.validation().status().name(),
                finalized.policyValidation().status().name(),
                ragAnswerCache.enabled() ? "MISS" : "BYPASS",
                finalized.outcome()));
        int memoryMessageCount = prepared.memory().history().size();
        if (!finalized.canonicalContent().isBlank()) {
            memoryMessageCount = appendMemory(prepared.memory(), prepared.chat().messages(), response);
            appendConversation(
                    principal,
                    prepared.memory(),
                    prepared.chat().messages().stream().map(this::toDomainMessage).toList(),
                    response);
        }

        Map<String, Object> metadata = new LinkedHashMap<>(
                terminal == null ? Map.of() : terminal.metadata().toMap());
        metadata.putAll(memoryMetadata(prepared.memory(), memoryMessageCount));
        metadata.putAll(withRagTiming(prepared, generationElapsedMs));
        metadata.put("canonicalContent", finalized.canonicalContent());
        metadata.put("citationValidationStatus", finalized.validation().status().name());
        metadata.put("answerPolicyValidationStatus", finalized.policyValidation().status().name());
        metadata.put("ragAnswerCache", ragAnswerCache.enabled() ? "MISS" : "BYPASS");
        metadata.putAll(ragOutcomeMetadata(prepared, finalized.outcome()));
        cacheRagAnswer(prepared, principal, model, finalized);
        ChatResponseMetadata completedMetadata = ChatResponseMetadata.from(metadata);
        modelUsageStore.record(completedMetadata, model, AiModelUsageRequestKind.RAG);
        logSlowRagChat(prepared, generationElapsedMs);
        return ChatStreamEvent.complete(model, completedMetadata);
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

    private ChatStreamEvent ragStreamError(String errorCode) {
        return ChatStreamEvent.error(
                "RAG 응답을 완료하지 못했습니다.",
                ChatResponseMetadata.from(Map.of("errorCode", errorCode)));
    }

    private record ObjectScope(String objectType, String objectId) {

        static ObjectScope none() {
            return new ObjectScope(null, null);
        }

        boolean hasFilter() {
            return objectType != null || objectId != null;
        }
    }

    private record PreparedRagChat(
            long requestStartedNanos,
            ChatRagRequestDto request,
            ObjectScope objectScope,
            ChatRequestDto chat,
            ChatRequestDto augmentedChat,
            ChatMemoryContext memory,
            RagRetrievalDiagnostics diagnostics,
            boolean exposeDiagnostics,
            Map<String, Object> ragMetadata,
            long retrievalElapsedMs,
            long overviewReductionElapsedMs,
            RagQueryIntentClassifier.Classification queryIntent,
            String retrievalMode,
            int resultCount,
            boolean overviewCacheHit,
            boolean skippedChat,
            ResolvedRagAnswerPolicy answerPolicy,
            ResolvedRagSourcePolicy sourcePolicy,
            RagExternalEvidenceService.Result externalEvidence,
            PackedEvidenceSet evidenceSet) {
    }

    private Optional<CachedRagHit> cachedRagAnswer(PreparedRagChat prepared, Principal principal) {
        Optional<RagAnswerCacheKey> key = ragAnswerCacheKey(prepared, principal);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String contextFingerprint = prepared.evidenceSet().contextFingerprint();
        return ragAnswerCache.get(key.get())
                .filter(answer -> answer.isValidFor(
                        contextFingerprint,
                        prepared.answerPolicy().fingerprint(),
                        Instant.now()))
                .filter(answer -> cachedAnswerIsCanonical(answer, prepared))
                .map(answer -> new CachedRagHit(key.get(), answer));
    }

    private boolean cachedAnswerIsCanonical(RagCachedAnswer answer, PreparedRagChat prepared) {
        if (!RagCitationValidator.Status.INDEX_VALID.name()
                .equals(answer.citationValidationStatus())) {
            return false;
        }
        RagAnswerFinalizer.FinalizedAnswer finalized =
                ragAnswerFinalizer.finalizeAnswer(
                        answer.canonicalContent(),
                        prepared.evidenceSet(),
                        prepared.answerPolicy(),
                        prepared.queryIntent());
        return finalized.validation().valid()
                && finalized.canonicalContent().equals(answer.canonicalContent());
    }

    private void cacheRagAnswer(
            PreparedRagChat prepared,
            Principal principal,
            String model,
            RagAnswerFinalizer.FinalizedAnswer finalized) {
        if (!finalized.validation().valid()
                || !finalized.policyValidation().valid()
                || finalized.canonicalContent().isBlank()) {
            return;
        }
        Optional<RagAnswerCacheKey> key = ragAnswerCacheKey(prepared, principal);
        if (key.isEmpty()) {
            return;
        }
        Instant createdAt = Instant.now();
        RagCachedAnswer answer = new RagCachedAnswer(
                finalized.canonicalContent(),
                model,
                finalized.validation().status().name(),
                prepared.evidenceSet().contextFingerprint(),
                prepared.answerPolicy().effectiveMode().name(),
                prepared.answerPolicy().fingerprint(),
                finalized.policyValidation().status().name(),
                finalized.outcome().partial(),
                finalized.outcome().originalValidationUnitCount(),
                finalized.outcome().omittedValidationUnitCount(),
                createdAt,
                createdAt.plus(ragAnswerCache.ttl()));
        ragAnswerCache.put(key.get(), answer);
    }

    private Optional<RagAnswerCacheKey> ragAnswerCacheKey(PreparedRagChat prepared, Principal principal) {
        if (!ragAnswerCache.enabled()
                || principal == null
                || prepared.memory() == null
                || prepared.memory().enabled()
                || (!prepared.objectScope().hasFilter()
                        && (prepared.request().indexedWebSources() == null
                                || prepared.request().indexedWebSources().isEmpty()))
                || prepared.evidenceSet().contextFingerprint().isBlank()) {
            return Optional.empty();
        }
        String question = normalizeText(prepared.request().ragQuery());
        if (question == null) {
            question = lastUserMessage(prepared.chat());
        }
        return Optional.of(RagAnswerCacheKey.create(
                currentPrincipalScope(principal),
                prepared.request(),
                question,
                deploymentOrProvider(prepared.chat()),
                prepared.evidenceSet().contextFingerprint(),
                prepared.answerPolicy(),
                prepared.sourcePolicy()));
    }

    private void writeCachedRagStream(
            OutputStream outputStream,
            String requestId,
            PreparedRagChat prepared,
            Principal principal,
            RagCachedAnswer answer) throws IOException {
        ChatResponse response = new ChatResponse(
                List.of(ChatMessage.assistant(answer.canonicalContent())),
                answer.model(),
                Map.of());
        RagAnswerFinalizer.FinalizedAnswer finalized = withCachedOutcome(
                ragAnswerFinalizer.finalizeAnswer(
                        answer.canonicalContent(),
                        prepared.evidenceSet(),
                        prepared.answerPolicy(),
                        prepared.queryIntent()),
                answer);
        response = responseWithMetadata(response, ragTurnMetadata(
                prepared,
                answer.canonicalContent(),
                answer.citationValidationStatus(),
                answer.policyValidationStatus(),
                "HIT",
                finalized.outcome()));
        int memoryMessageCount = appendMemory(prepared.memory(), prepared.chat().messages(), response);
        appendConversation(
                principal,
                prepared.memory(),
                prepared.chat().messages().stream().map(this::toDomainMessage).toList(),
                response);
        Map<String, Object> metadata = new LinkedHashMap<>(
                memoryMetadata(prepared.memory(), memoryMessageCount));
        metadata.putAll(withRagTiming(prepared, 0L));
        metadata.put("canonicalContent", answer.canonicalContent());
        metadata.put("citationValidationStatus", answer.citationValidationStatus());
        metadata.put("answerPolicyValidationStatus", answer.policyValidationStatus());
        metadata.put("ragAnswerCache", "HIT");
        metadata.putAll(ragOutcomeMetadata(prepared, finalized.outcome()));
        writeSse(outputStream, requestId,
                ChatStreamEvent.complete(answer.model(), ChatResponseMetadata.from(metadata)));
    }

    private RagAnswerFinalizer.FinalizedAnswer withCachedOutcome(
            RagAnswerFinalizer.FinalizedAnswer finalized,
            RagCachedAnswer cached) {
        if (!cached.partial()) {
            return finalized;
        }
        RagAnswerOutcome base = finalized.outcome();
        RagAnswerOutcome outcome = new RagAnswerOutcome(
                base.type(),
                base.stage(),
                base.reasonCode(),
                base.retrievedResultCount(),
                base.acceptedResultCount(),
                base.packedEvidenceCount(),
                base.usedEvidenceIndexes(),
                base.citationValidationStatus(),
                base.policyValidationStatus(),
                base.validationUnitCount(),
                base.citedValidationUnitCount(),
                true,
                cached.originalValidationUnitCount(),
                cached.omittedValidationUnitCount());
        return new RagAnswerFinalizer.FinalizedAnswer(
                finalized.canonicalContent(),
                finalized.validation(),
                finalized.policyValidation(),
                outcome);
    }

    private record CachedRagHit(RagAnswerCacheKey key, RagCachedAnswer answer) {
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
