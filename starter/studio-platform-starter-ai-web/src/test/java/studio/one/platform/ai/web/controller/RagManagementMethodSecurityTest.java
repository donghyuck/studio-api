package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.IndexRequest;
import studio.one.platform.ai.web.dto.SearchRequest;

class RagManagementMethodSecurityTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchRejectsMissingObjectReadBeforeSearching() {
        try (SecuredControllers secured = securedControllers(Set.of(permission("services:ai_rag", "read")))) {
            RagController controller = secured.ragController();

            assertThrows(AccessDeniedException.class,
                    () -> controller.search(new SearchRequest("hello", 3, "attachment", "42")));

            verify(secured.ragPipelineService(), never()).search(any(RagSearchRequest.class));
        }
    }

    @Test
    void searchAllowsScopedObjectReadAndRejectsEmptyScopeInController() {
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "read"),
                permission("objects:attachment:42", "read")))) {
            RagController controller = secured.ragController();

            assertThatNoException().isThrownBy(
                    () -> controller.search(new SearchRequest("hello", 3, "attachment", "42")));
            assertThrows(ResponseStatusException.class,
                    () -> controller.search(new SearchRequest("hello", 3, null, null)));
        }
    }

    @Test
    void legacyIndexRejectsReadFallbackBeforeMutatingPipeline() {
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "read"),
                permission("objects:attachment:42", "write")))) {
            RagController controller = secured.ragController();

            assertThrows(AccessDeniedException.class,
                    () -> controller.index(indexRequest("attachment", "42")));

            verify(secured.ragPipelineService(), never()).index(any());
        }
    }

    @Test
    void legacyIndexRejectsMissingObjectWriteBeforeMutatingPipeline() {
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "write"),
                permission("objects:attachment:41", "write")))) {
            RagController controller = secured.ragController();

            assertThrows(AccessDeniedException.class,
                    () -> controller.index(indexRequest("attachment", "42")));

            verify(secured.ragPipelineService(), never()).index(any());
        }
    }

    @Test
    void legacyIndexAllowsStrictWriteWithObjectWrite() {
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "write"),
                permission("objects:attachment:42", "write")))) {
            RagController controller = secured.ragController();

            assertThatNoException().isThrownBy(
                    () -> controller.index(indexRequest("attachment", "42")));

            verify(secured.ragPipelineService()).index(any());
        }
    }

    @Test
    void objectChunkRoutesRequireCurrentObjectReadBeforeContentLookup() {
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "read"),
                permission("features:attachment", "read")))) {
            RagIndexJobController controller = secured.jobController();

            assertThrows(AccessDeniedException.class,
                    () -> controller.objectChunks("attachment", "42", PageRequest.of(0, 10)));
            assertThrows(AccessDeniedException.class,
                    () -> controller.objectChunksPage("attachment", "42", PageRequest.of(0, 10)));

            verify(secured.ragPipelineService(), never()).listByObject("attachment", "42", 0, 10);
        }
    }

    @Test
    void objectMetadataRequiresCurrentObjectReadBeforeMetadataLookup() {
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "read"),
                permission("features:attachment", "read")))) {
            RagIndexJobController controller = secured.jobController();

            assertThrows(AccessDeniedException.class,
                    () -> controller.objectMetadata("attachment", "42"));

            verify(secured.vectorStorePort(), never()).getMetadata("attachment", "42");
        }
    }

    @Test
    void jobChunkRoutesAuthorizeStoredJobObjectScope() {
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "read"),
                permission("features:attachment", "read"),
                permission("objects:attachment:42", "read")))) {
            RagIndexJobController controller = secured.jobController();

            assertThatNoException().isThrownBy(
                    () -> controller.getJobChunks("job-1", PageRequest.of(0, 10)));
            assertThatNoException().isThrownBy(
                    () -> controller.getJobChunksPage("job-1", PageRequest.of(0, 10)));

            verify(secured.ragPipelineService(), times(2)).listByObject("attachment", "42", 0, 10);
        }
    }

    @Test
    void jobChunkRoutesDenyMissingStoredObjectReadBeforeChunkLookup() {
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "read"),
                permission("features:attachment", "read")))) {
            RagIndexJobController controller = secured.jobController();

            assertThrows(AccessDeniedException.class,
                    () -> controller.getJobChunks("job-1", PageRequest.of(0, 10)));
            assertThrows(AccessDeniedException.class,
                    () -> controller.getJobChunksPage("job-1", PageRequest.of(0, 10)));

            verify(secured.ragPipelineService(), never()).listByObject("attachment", "42", 0, 10);
        }
    }

    @Test
    void jobChunkRoutesDenyBlankStoredObjectScopeBeforeChunkLookup() {
        RagIndexJob blankTypeJob = job("job-blank-type", " ", "42");
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "read"),
                permission("features:attachment", "read"),
                permission("objects:attachment:42", "read")), blankTypeJob)) {
            RagIndexJobController controller = secured.jobController();

            assertThrows(AccessDeniedException.class,
                    () -> controller.getJobChunks("job-blank-type", PageRequest.of(0, 10)));
            assertThrows(AccessDeniedException.class,
                    () -> controller.getJobChunksPage("job-blank-type", PageRequest.of(0, 10)));

            verify(secured.ragPipelineService(), never()).listByObject(anyString(), anyString(), anyInt(), anyInt());
            verify(secured.ragPipelineService(), never()).countByObject(anyString(), anyString());
        }
    }

    @Test
    void jobChunkRoutesDenyPartialStoredObjectScopeBeforeChunkLookup() {
        RagIndexJob missingIdJob = job("job-missing-id", "attachment", null);
        try (SecuredControllers secured = securedControllers(Set.of(
                permission("services:ai_rag", "read"),
                permission("features:attachment", "read"),
                permission("objects:attachment:42", "read")), missingIdJob)) {
            RagIndexJobController controller = secured.jobController();

            assertThrows(AccessDeniedException.class,
                    () -> controller.getJobChunks("job-missing-id", PageRequest.of(0, 10)));
            assertThrows(AccessDeniedException.class,
                    () -> controller.getJobChunksPage("job-missing-id", PageRequest.of(0, 10)));

            verify(secured.ragPipelineService(), never()).listByObject(anyString(), anyString(), anyInt(), anyInt());
            verify(secured.ragPipelineService(), never()).countByObject(anyString(), anyString());
        }
    }

    private SecuredControllers securedControllers(Set<String> permissions) {
        return securedControllers(permissions, job("job-1", "attachment", "42"));
    }

    private SecuredControllers securedControllers(Set<String> permissions, RagIndexJob job) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "n/a", List.of()));

        RagIndexJobService jobService = mock(RagIndexJobService.class);
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);
        VectorStorePort vectorStorePort = mock(VectorStorePort.class);

        when(jobService.getJob(job.jobId())).thenReturn(Optional.of(job));
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "content", Map.of(), 0.9d)));
        when(ragPipelineService.listByObject("attachment", "42", 0, 10))
                .thenReturn(List.of(new RagSearchResult("doc-1", "chunk", Map.of(), 0.8d)));
        when(ragPipelineService.countByObject("attachment", "42")).thenReturn(1L);
        when(vectorStorePort.getMetadata("attachment", "42")).thenReturn(Map.of("documentId", "doc-1"));

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean("endpointAuthz", EndpointAuthzStub.class, () -> new EndpointAuthzStub(permissions));
        context.registerBean("ragObjectAuthorizationRouter", RagObjectAuthorizationRouter.class,
                () -> new RagObjectAuthorizationRouter(
                        context,
                        List.of(new PermissionBackedObjectAuthorizer(permissions))));
        context.registerBean("ragIndexJobEndpointSecurity", RagIndexJobEndpointSecurity.class,
                () -> new RagIndexJobEndpointSecurity(jobService));
        context.registerBean(RagController.class, () -> new RagController(ragPipelineService));
        context.registerBean(RagIndexJobController.class,
                () -> new RagIndexJobController(jobService, ragPipelineService, vectorStorePort));
        context.refresh();
        return new SecuredControllers(
                context,
                context.getBean(RagController.class),
                context.getBean(RagIndexJobController.class),
                ragPipelineService,
                vectorStorePort);
    }

    private static RagIndexJob job(String jobId, String objectType, String objectId) {
        return RagIndexJob.pending(
                jobId,
                objectType,
                objectId,
                "doc-1",
                "attachment",
                Instant.parse("2026-04-26T00:00:00Z"));
    }

    private static String permission(String resource, String action) {
        return resource.toLowerCase(Locale.ROOT) + ":" + action.toLowerCase(Locale.ROOT);
    }

    private IndexRequest indexRequest(String objectType, String objectId) {
        return new IndexRequest(
                "doc-1",
                "hello",
                Map.of("objectType", objectType, "objectId", objectId),
                List.of(),
                false);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {
    }

    private record SecuredControllers(
            AnnotationConfigApplicationContext context,
            RagController ragController,
            RagIndexJobController jobController,
            RagPipelineService ragPipelineService,
            VectorStorePort vectorStorePort) implements AutoCloseable {

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

        public boolean canStrict(String resource, String action) {
            return can(resource, action);
        }
    }

    private record PermissionBackedObjectAuthorizer(Set<String> permissions) implements RagObjectAuthorizer {

        @Override
        public boolean supports(String objectType) {
            return true;
        }

        @Override
        public boolean canRead(String objectType, String objectId) {
            return permissions.contains(permission("objects:" + objectType + ":" + objectId, "read"))
                    || permissions.contains(permission("objects:" + objectType, "read"));
        }

        @Override
        public boolean canWrite(String objectType, String objectId) {
            return permissions.contains(permission("objects:" + objectType + ":" + objectId, "write"))
                    || permissions.contains(permission("objects:" + objectType, "write"));
        }
    }
}
