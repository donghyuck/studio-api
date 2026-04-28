package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import studio.one.platform.ai.web.dto.ChatResponseDto;
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

    private SecuredController securedController(Set<String> permissions) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "n/a", List.of()));

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean("endpointAuthz", EndpointAuthzStub.class, () -> new EndpointAuthzStub(permissions));
        context.registerBean(ChatController.class, this::controller);
        context.refresh();
        return new SecuredController(context, context.getBean(ChatController.class));
    }

    private ChatController controller() {
        AiProviderRegistry providerRegistry = mock(AiProviderRegistry.class);
        ChatPort chatPort = mock(ChatPort.class);
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);

        when(providerRegistry.chatPort(null)).thenReturn(chatPort);
        when(chatPort.chat(any(ChatRequest.class))).thenReturn(response());
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "content", Map.of(), 0.9d)));
        when(ragPipelineService.latestDiagnostics()).thenReturn(java.util.Optional.empty());

        return new ChatController(providerRegistry, ragPipelineService);
    }

    private ChatRagRequestDto ragRequest(String objectType, String objectId) {
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
                objectId);
    }

    private ChatResponse response() {
        return new ChatResponse(
                List.of(studio.one.platform.ai.core.chat.ChatMessage.assistant("ok")),
                "model",
                Map.of());
    }

    private static String permission(String resource, String action) {
        return resource.toLowerCase(Locale.ROOT) + ":" + action.toLowerCase(Locale.ROOT);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {
    }

    private record SecuredController(
            AnnotationConfigApplicationContext context,
            ChatController controller) implements AutoCloseable {

        @Override
        public void close() {
            context.close();
        }
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
