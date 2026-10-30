package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import tools.jackson.databind.json.JsonMapper;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
import studio.one.platform.ai.core.rag.indexed.ResolvedIndexedRagSource;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.cache.RagAnswerCache;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.dto.ChatResponseDto;
import studio.one.platform.ai.web.dto.IndexedWebSourceRefDto;
import studio.one.platform.ai.web.dto.RagRegenerateRequestDto;
import studio.one.platform.web.dto.ApiResponse;

class ChatControllerMethodSecurityTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ragChatRejectsMissingRagReadAuthority() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("objects:2001:6", "read")))) {
            ChatController controller = secured.controller();

            assertThrows(AccessDeniedException.class,
                    () -> controller.chatWithRag(ragRequest("2001", "6"), null));
        }
    }

    @Test
    void ragChatRejectsAttachmentScopeWithoutAttachmentReadAuthority() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("services:ai_rag", "read")))) {
            ChatController controller = secured.controller();

            assertThrows(AccessDeniedException.class,
                    () -> controller.chatWithRag(ragRequest("attachment", "6"), null));
        }
    }

    @Test
    void ragChatAllowsGenericScopeWithObjectInstanceReadAuthority() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("services:ai_rag", "read"),
                permission("objects:2001:6", "read")))) {
            ChatController controller = secured.controller();

            assertThatNoException().isThrownBy(
                    () -> controller.chatWithRag(ragRequest("2001", "6"), null));
        }
    }

    @Test
    void ragChatAllowsGenericScopeWithObjectTypeReadAuthority() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("services:ai_rag", "read"),
                permission("objects:2001", "read")))) {
            ChatController controller = secured.controller();

            assertThatNoException().isThrownBy(
                    () -> controller.chatWithRag(ragRequest("2001", "6"), null));
        }
    }

    @Test
    void ragChatAllowsObjectAndIndexedSourcesWhenAllScopesAreAuthorized() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("services:ai_rag", "read"),
                permission("objects:2001:6", "read"),
                permission("objects:web_source:source-a", "read")))) {
            ChatController controller = secured.controller();

            ResponseEntity<ApiResponse<ChatResponseDto>> response = controller.chatWithRag(
                    ragRequest("2001", "6", List.of(new IndexedWebSourceRefDto("source-a", "revision-a"))),
                    null);

            org.assertj.core.api.Assertions.assertThat(response.getBody().getData().metadata())
                    .containsKey("indexedWebSources");
        }
    }

    @Test
    void ragChatRejectsPartialObjectScopeBeforeControllerExecution() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("services:ai_rag", "read")))) {
            ChatController controller = secured.controller();

            assertThrows(AccessDeniedException.class,
                    () -> controller.chatWithRag(ragRequest("2001", null), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
            assertThrows(AccessDeniedException.class,
                    () -> controller.chatWithRag(ragRequest(null, "6"), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
        }
    }

    @Test
    void ragChatRejectsUnscopedAndEmptyIndexedRequestsBeforeControllerExecution() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("services:ai_rag", "read")))) {
            ChatController controller = secured.controller();

            assertThrows(AccessDeniedException.class,
                    () -> controller.chatWithRag(ragRequest(null, null), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
            assertThrows(AccessDeniedException.class,
                    () -> controller.chatWithRag(ragRequest(null, null, List.of()), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
        }
    }

    @Test
    void ragStreamRejectsUnscopedEmptyIndexedAndPartialObjectScopeBeforeControllerExecution() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("services:ai_rag", "read")))) {
            ChatController controller = secured.controller();

            assertThrows(AccessDeniedException.class,
                    () -> controller.streamWithRag(ragRequest(null, null), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
            assertThrows(AccessDeniedException.class,
                    () -> controller.streamWithRag(ragRequest(null, null, List.of()), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
            assertThrows(AccessDeniedException.class,
                    () -> controller.streamWithRag(ragRequest("2001", null), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
            assertThrows(AccessDeniedException.class,
                    () -> controller.streamWithRag(ragRequest(null, "6"), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
        }
    }

    @Test
    void ragRegenerateRejectsUnscopedEmptyIndexedAndPartialObjectScopeBeforeControllerExecution() {
        try (SecuredController secured = securedController(Set.of(
                permission("services:ai_chat", "write"),
                permission("services:ai_rag", "read")))) {
            ChatController controller = secured.controller();

            assertThrows(AccessDeniedException.class,
                    () -> controller.regenerateWithRag(regenerateRequest(ragRequest(null, null)), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
            assertThrows(AccessDeniedException.class,
                    () -> controller.regenerateWithRag(regenerateRequest(ragRequest(null, null, List.of())), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
            assertThrows(AccessDeniedException.class,
                    () -> controller.regenerateWithRag(regenerateRequest(ragRequest(null, "6")), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
            assertThrows(AccessDeniedException.class,
                    () -> controller.regenerateWithRag(regenerateRequest(ragRequest("2001", null)), null));
            verifyNoInteractions(secured.chatPort(), secured.ragPipelineService());
        }
    }

    private SecuredController securedController(Set<String> permissions) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "n/a", List.of()));

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean("endpointAuthz", EndpointAuthzStub.class, () -> new EndpointAuthzStub(permissions));
        RagObjectAuthorizationRouter authorizationRouter = new RagObjectAuthorizationRouter(context);
        context.registerBean("ragObjectAuthorizationRouter", RagObjectAuthorizationRouter.class,
                () -> authorizationRouter);
        ControllerFixture fixture = controller(authorizationRouter);
        context.registerBean(ChatController.class, () -> fixture.controller());
        context.refresh();
        return new SecuredController(
                context,
                context.getBean(ChatController.class),
                fixture.chatPort(),
                fixture.ragPipelineService());
    }

    private ControllerFixture controller(RagObjectAuthorizationRouter authorizationRouter) {
        AiProviderRegistry providerRegistry = mock(AiProviderRegistry.class);
        ChatPort chatPort = mock(ChatPort.class);
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);

        when(providerRegistry.chatPort(null)).thenReturn(chatPort);
        when(chatPort.chat(any(ChatRequest.class))).thenReturn(response());
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "content", Map.of(), 0.9d)));
        when(ragPipelineService.latestDiagnostics()).thenReturn(java.util.Optional.empty());

        ChatController controller = new ChatController(
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
                RagAnswerCache.noop(),
                RagAnswerPolicyResolver.defaults(),
                new RagAnswerPromptComposer(),
                new RagAnswerFinalizer(),
                authorizationRouter);
        controller.setIndexedRagSourceProviders(List.of(indexedProvider()));
        return new ControllerFixture(controller, chatPort, ragPipelineService);
    }

    private ChatRagRequestDto ragRequest(String objectType, String objectId) {
        return ragRequest(objectType, objectId, null);
    }

    private ChatRagRequestDto ragRequest(
            String objectType,
            String objectId,
            List<IndexedWebSourceRefDto> indexedWebSources) {
        return new ChatRagRequestDto(
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
                objectType,
                objectId,
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
                indexedWebSources,
                null,
                null);
    }

    private ChatResponse response() {
        return new ChatResponse(
                List.of(studio.one.platform.ai.core.chat.ChatMessage.assistant("ok")),
                "model",
                Map.of());
    }

    private RagRegenerateRequestDto regenerateRequest(ChatRagRequestDto rag) {
        return new RagRegenerateRequestDto("chat-1", rag);
    }

    private static String permission(String resource, String action) {
        return resource.toLowerCase(Locale.ROOT) + ":" + action.toLowerCase(Locale.ROOT);
    }

    private IndexedRagSourceProvider indexedProvider() {
        return new IndexedRagSourceProvider() {
            @Override
            public boolean supports(String sourceType) {
                return "web_source".equals(sourceType);
            }

            @Override
            public Optional<ResolvedIndexedRagSource> resolve(String sourceId, String revisionId) {
                if (!"source-a".equals(sourceId) || !"revision-a".equals(revisionId)) {
                    return Optional.empty();
                }
                return Optional.of(new ResolvedIndexedRagSource(
                        "web_source",
                        sourceId,
                        revisionId,
                        "web_source",
                        sourceId,
                        "hash-a",
                        "embedding-default",
                        "space-1",
                        Map.of()));
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {
    }

    private record SecuredController(
            AnnotationConfigApplicationContext context,
            ChatController controller,
            ChatPort chatPort,
            RagPipelineService ragPipelineService) implements AutoCloseable {

        @Override
        public void close() {
            context.close();
        }
    }

    private record ControllerFixture(
            ChatController controller,
            ChatPort chatPort,
            RagPipelineService ragPipelineService) {
    }

    static class EndpointAuthzStub {

        private final Set<String> permissions;

        EndpointAuthzStub(Set<String> permissions) {
            this.permissions = permissions;
        }

        public boolean can(String resource, String action) {
            return permissions.contains(permission(resource.trim(), action));
        }
    }
}
