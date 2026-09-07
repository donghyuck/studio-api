package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.cache.CaffeineRagAnswerCache;
import studio.one.platform.ai.web.cache.TeamRagCacheScope;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.dto.ChatResponseDto;

class ChatControllerTeamRagIntegrationTest {

    @Test
    void teamScopeIsSharedBySyncCacheAndSseFlows() throws Exception {
        AiProviderRegistry providers = mock(AiProviderRegistry.class);
        ChatPort chatPort = mock(ChatPort.class);
        when(providers.chatPort(null)).thenReturn(chatPort);
        when(chatPort.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(
                List.of(ChatMessage.assistant("team answer [1]")), "model", Map.of()));
        RagPipelineService pipeline = mock(RagPipelineService.class);
        when(pipeline.latestDiagnostics()).thenReturn(Optional.<RagRetrievalDiagnostics>empty());
        TeamRagRetrievalService teamRetrieval = mock(TeamRagRetrievalService.class);
        TeamRagCitationGuard citationGuard = mock(TeamRagCitationGuard.class);
        var retrieval = retrieval();
        when(teamRetrieval.retrieveQueries(eq(7L), eq(2L), anyList())).thenReturn(Optional.of(retrieval));
        ChatController controller = controller(providers, pipeline);
        controller.setTeamRagServices(teamRetrieval, citationGuard, 32);
        ChatRagRequestDto request = request();
        Principal principal = () -> "team-user";

        ChatResponseDto first = controller.chatWithRag(request, principal).getBody().getData();
        ChatResponseDto second = controller.chatWithRag(request, principal).getBody().getData();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        controller.streamWithRag(request, principal).getBody().writeTo(stream);

        assertThat(first.metadata()).containsKey("teamRagScope");
        @SuppressWarnings("unchecked")
        Map<String, Object> teamScope = (Map<String, Object>) first.metadata().get("teamRagScope");
        assertThat(teamScope)
                .containsEntry("teamId", 7L)
                .containsEntry("workspaceId", 2L)
                .containsEntry("permissionVersion", "permission-1");
        assertThat(second.metadata()).containsEntry("ragAnswerCache", "HIT");
        assertThat(stream.toString(StandardCharsets.UTF_8))
                .contains("event: complete", "\"ragAnswerCache\":\"HIT\"", "\"teamRagScope\"")
                .doesNotContain("event: delta");
        verify(chatPort, times(1)).chat(any(ChatRequest.class));
        verify(teamRetrieval, times(3)).retrieveQueries(eq(7L), eq(2L), anyList());
    }

    @Test
    void rejectsTeamRequestWhenCapabilityIsUnavailable() {
        AiProviderRegistry providers = mock(AiProviderRegistry.class);
        RagPipelineService pipeline = mock(RagPipelineService.class);
        ChatController controller = new ChatController(providers, pipeline, JsonMapper.builder().build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.chatWithRag(request(), () -> "user"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_IMPLEMENTED));
        verifyNoInteractions(providers, pipeline);
    }

    @Test
    void capabilitiesAdvertiseTeamRagOnlyAfterServicesAreWired() {
        AiProviderRegistry providers = mock(AiProviderRegistry.class);
        RagPipelineService pipeline = mock(RagPipelineService.class);
        ChatController controller = new ChatController(providers, pipeline, JsonMapper.builder().build());

        assertThat(controller.ragCapabilities().getBody().getData().teamRag().enabled()).isFalse();

        controller.setTeamRagServices(
                mock(TeamRagRetrievalService.class), mock(TeamRagCitationGuard.class), 32);
        assertThat(controller.ragCapabilities().getBody().getData().teamRag().enabled()).isTrue();
        assertThat(controller.ragCapabilities().getBody().getData().teamRag().maxObjectScopes()).isEqualTo(32);
    }

    @Test
    void interpretiveTeamQuestionUsesConversationAwareMultiQueryPlan() {
        AiProviderRegistry providers = mock(AiProviderRegistry.class);
        ChatPort chatPort = mock(ChatPort.class);
        when(providers.chatPort(null)).thenReturn(chatPort);
        when(chatPort.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(
                List.of(ChatMessage.assistant("팀 문서에서 확인되는 이유입니다. [1]")), "model", Map.of()));
        RagPipelineService pipeline = mock(RagPipelineService.class);
        when(pipeline.latestDiagnostics()).thenReturn(Optional.empty());
        TeamRagRetrievalService teamRetrieval = mock(TeamRagRetrievalService.class);
        TeamRagCitationGuard citationGuard = mock(TeamRagCitationGuard.class);
        TeamRagRetrievalService.RetrievalResult base = retrieval();
        TeamRagRetrievalService.RetrievalResult multi = new TeamRagRetrievalService.RetrievalResult(
                base.results(), base.manifest(), base.cacheScope(), base.citations(),
                3, 2, 1, true, false);
        when(teamRetrieval.retrieveQueries(eq(7L), eq(2L), anyList())).thenReturn(Optional.of(multi));
        ChatController controller = controller(providers, pipeline);
        controller.setTeamRagServices(teamRetrieval, citationGuard, 32);
        ChatRagRequestDto request = request(
                "왜 이 규칙이 필요한가",
                List.of(
                        new ChatMessageDto("user", "출퇴근 규칙을 알려줘"),
                        new ChatMessageDto("assistant", "이전 답변"),
                        new ChatMessageDto("user", "왜 이 규칙이 필요한가")));

        ChatResponseDto response = controller.chatWithRag(request, () -> "team-user").getBody().getData();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RagSearchRequest>> queries = ArgumentCaptor.forClass(List.class);
        verify(teamRetrieval).retrieveQueries(eq(7L), eq(2L), queries.capture());
        assertThat(queries.getValue()).hasSize(3);
        assertThat(queries.getValue().get(0).query()).contains("출퇴근 규칙을 알려줘");
        assertThat(response.metadata())
                .containsEntry("teamRagExecutedQueryCount", 3)
                .containsEntry("teamRagRoutingApplied", true)
                .containsEntry("ragInterpretiveConversationContextUsed", true);
    }

    private ChatController controller(AiProviderRegistry providers, RagPipelineService pipeline) {
        return new ChatController(
                providers,
                pipeline,
                new RagChatRetrievalService(pipeline),
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
    }

    private TeamRagRetrievalService.RetrievalResult retrieval() {
        TeamKnowledgeManifest manifest = TeamKnowledgeManifest.create(
                7L,
                2L,
                "corpus-1",
                "permission-1",
                List.of(new TeamKnowledgeSourceRef(
                        7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                        "attachment", "10", "rev-1", Set.of())));
        RagSearchResult result = new RagSearchResult(
                "document-1",
                "team evidence",
                Map.of(
                        "objectType", "attachment",
                        "objectId", "10",
                        "revisionId", "rev-1",
                        "chunkId", "chunk-1",
                        "sourceRef", "page-1"),
                0.9d);
        return new TeamRagRetrievalService.RetrievalResult(
                List.of(result),
                manifest,
                TeamRagCacheScope.from(manifest),
                List.of(new TeamRagCitationRef(2L, "attachment", "10", "rev-1")));
    }

    private ChatRagRequestDto request() {
        return request("question", List.of(new ChatMessageDto("user", "question")));
    }

    private ChatRagRequestDto request(String question, List<ChatMessageDto> messages) {
        return new ChatRagRequestDto(
                new ChatRequestDto(
                        null, null, messages,
                        null, null, null, null, null, null),
                question,
                3,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                7L,
                2L);
    }
}
