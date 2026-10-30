package studio.one.application.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.web.controller.AttachmentEmbeddingPipelineController;
import studio.one.application.web.service.AttachmentRagIndexJobSourceExecutor;
import studio.one.application.web.service.AttachmentRagIndexJobSourceNameResolver;
import studio.one.application.web.service.AttachmentRagIndexService;
import studio.one.application.web.service.AttachmentStructuredRagIndexer;
import studio.one.application.web.service.DefaultAttachmentStructuredRagIndexer;
import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.chunking.artifact.ChunkSetStore;
import studio.one.platform.security.authz.DomainPolicyRegistry;
import studio.one.platform.security.authz.EndpointAuthorizationImpl;
import studio.one.platform.security.authz.EndpointModeGuard;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;

class ContentEmbeddingPipelineAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ContentEmbeddingPipelineAutoConfiguration.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersAttachmentRagIndexServicesWhenAttachmentServiceExists() {
        contextRunner
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentRagIndexService.class);
                    assertThat(context).hasSingleBean(AttachmentRagIndexJobSourceExecutor.class);
                    assertThat(context).hasSingleBean(AttachmentRagIndexJobSourceNameResolver.class);
                    assertThat(context).hasSingleBean(AttachmentEmbeddingPipelineController.class);
                });
    }

    @Test
    void proxiedRagIndexUsesStrictFeatureWriteAndAttachmentObjectMutation() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        FileContentExtractionService extractionService = mock(FileContentExtractionService.class);
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);
        Attachment attachment = mock(Attachment.class);

        when(attachment.getAttachmentId()).thenReturn(7L);
        when(attachment.getObjectType()).thenReturn(2103);
        when(attachment.getObjectId()).thenReturn(34L);
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getSize()).thenReturn(5L);
        when(attachmentService.getAttachmentById(7L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        contextRunner
                .withUserConfiguration(MethodSecurityConfig.class)
                .withBean(AttachmentService.class, () -> attachmentService)
                .withBean(FileContentExtractionService.class, () -> extractionService)
                .withBean(RagPipelineService.class, () -> ragPipelineService)
                .withBean(RagObjectAuthorizer.class, () -> new TestRagObjectAuthorizer("7"))
                .withBean("endpointAuthz", EndpointAuthorizationImpl.class,
                        () -> new EndpointAuthorizationImpl(policyRegistry(), allowAllModes()))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AttachmentEmbeddingPipelineController controller =
                            context.getBean(AttachmentEmbeddingPipelineController.class);
                    authenticate("ATTACHMENT_WRITE");

                    assertThatCode(() -> controller.ragIndex(7L, null)).doesNotThrowAnyException();

                    verify(ragPipelineService).index(any(), any());
                });
    }

    @Test
    void proxiedRagIndexRejectsFeatureReadFallbackBeforeHandler() {
        AttachmentService attachmentService = mock(AttachmentService.class);
        FileContentExtractionService extractionService = mock(FileContentExtractionService.class);
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);

        contextRunner
                .withUserConfiguration(MethodSecurityConfig.class)
                .withBean(AttachmentService.class, () -> attachmentService)
                .withBean(FileContentExtractionService.class, () -> extractionService)
                .withBean(RagPipelineService.class, () -> ragPipelineService)
                .withBean(RagObjectAuthorizer.class, () -> new TestRagObjectAuthorizer("7"))
                .withBean("endpointAuthz", EndpointAuthorizationImpl.class,
                        () -> new EndpointAuthorizationImpl(policyRegistry(), allowAllModes()))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AttachmentEmbeddingPipelineController controller =
                            context.getBean(AttachmentEmbeddingPipelineController.class);
                    authenticate("ATTACHMENT_READ");

                    assertThatThrownBy(() -> controller.ragIndex(7L, null))
                            .isInstanceOf(AccessDeniedException.class);
                    verifyNoInteractions(attachmentService, extractionService, ragPipelineService);
                });
    }

    @Test
    void proxiedRagIndexRejectsMissingAttachmentObjectMutationBeforeIndexing() {
        AttachmentService attachmentService = mock(AttachmentService.class);
        FileContentExtractionService extractionService = mock(FileContentExtractionService.class);
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);

        contextRunner
                .withUserConfiguration(MethodSecurityConfig.class)
                .withBean(AttachmentService.class, () -> attachmentService)
                .withBean(FileContentExtractionService.class, () -> extractionService)
                .withBean(RagPipelineService.class, () -> ragPipelineService)
                .withBean(RagObjectAuthorizer.class, () -> new TestRagObjectAuthorizer("8"))
                .withBean("endpointAuthz", EndpointAuthorizationImpl.class,
                        () -> new EndpointAuthorizationImpl(policyRegistry(), allowAllModes()))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AttachmentEmbeddingPipelineController controller =
                            context.getBean(AttachmentEmbeddingPipelineController.class);
                    authenticate("ATTACHMENT_WRITE");

                    assertThatThrownBy(() -> controller.ragIndex(7L, null))
                            .isInstanceOf(ResponseStatusException.class)
                            .extracting("statusCode")
                            .isEqualTo(HttpStatus.FORBIDDEN);
                    verifyNoInteractions(attachmentService, extractionService, ragPipelineService);
                });
    }

    @Test
    void suppliesChunkSetStoreToStructuredRagIndexer() {
        ChunkSetStore chunkSetStore = mock(ChunkSetStore.class);

        contextRunner
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .withBean(ChunkSetStore.class, () -> chunkSetStore)
                .run(context -> {
                    DefaultAttachmentStructuredRagIndexer indexer =
                            context.getBean(DefaultAttachmentStructuredRagIndexer.class);
                    ObjectProvider<?> provider = (ObjectProvider<?>) ReflectionTestUtils.getField(
                            indexer, "chunkSetStoreProvider");

                    assertThat(provider).isNotNull();
                    assertThat(provider.getIfAvailable()).isSameAs(chunkSetStore);
                });
    }

    @Test
    void backsOffWhenUserDefinedAttachmentRagIndexServiceExists() {
        AttachmentRagIndexService userService = mock(AttachmentRagIndexService.class);

        contextRunner
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .withBean(AttachmentRagIndexService.class, () -> userService)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentRagIndexService.class);
                    assertThat(context.getBean(AttachmentRagIndexService.class)).isSameAs(userService);
                });
    }

    @Test
    void backsOffWhenUserDefinedAttachmentRagIndexJobSourceExecutorExists() {
        AttachmentRagIndexJobSourceExecutor userExecutor = mock(AttachmentRagIndexJobSourceExecutor.class);

        contextRunner
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .withBean(AttachmentRagIndexJobSourceExecutor.class, () -> userExecutor)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentRagIndexJobSourceExecutor.class);
                    assertThat(context.getBean(AttachmentRagIndexJobSourceExecutor.class)).isSameAs(userExecutor);
                });
    }

    @Test
    void backsOffWhenUserDefinedAttachmentRagIndexJobSourceNameResolverExists() {
        AttachmentRagIndexJobSourceNameResolver userResolver = mock(AttachmentRagIndexJobSourceNameResolver.class);

        contextRunner
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .withBean(AttachmentRagIndexJobSourceNameResolver.class, () -> userResolver)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentRagIndexJobSourceNameResolver.class);
                    assertThat(context.getBean(AttachmentRagIndexJobSourceNameResolver.class)).isSameAs(userResolver);
                });
    }

    @Test
    void registersAttachmentTeamKnowledgeSourceContributor() {
        contextRunner
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TeamKnowledgeSourceContributor.class);
                });
    }

    @Test
    void backsOffWhenUserDefinedStructuredRagIndexerExists() {
        AttachmentStructuredRagIndexer userIndexer = mock(AttachmentStructuredRagIndexer.class);

        contextRunner
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .withBean(AttachmentStructuredRagIndexer.class, () -> userIndexer)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentStructuredRagIndexer.class);
                    assertThat(context.getBean(AttachmentStructuredRagIndexer.class)).isSameAs(userIndexer);
                    assertThat(context).doesNotHaveBean(DefaultAttachmentStructuredRagIndexer.class);
                });
    }

    @Test
    void skipsStructuredRagIndexerWhenChunkingIsNotAvailable() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("studio.one.platform.chunking"))
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentRagIndexService.class);
                    assertThat(context).doesNotHaveBean(AttachmentStructuredRagIndexer.class);
                    assertThat(context).doesNotHaveBean(DefaultAttachmentStructuredRagIndexer.class);
                });
    }

    @Test
    void backsOffWhenAttachmentServiceIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AttachmentRagIndexService.class);
            assertThat(context).doesNotHaveBean(AttachmentRagIndexJobSourceExecutor.class);
            assertThat(context).doesNotHaveBean(AttachmentRagIndexJobSourceNameResolver.class);
            assertThat(context).doesNotHaveBean(AttachmentEmbeddingPipelineController.class);
        });
    }

    private static void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user",
                "n/a",
                List.of(new SimpleGrantedAuthority(role))));
    }

    private static DomainPolicyRegistry policyRegistry() {
        return (resource, action) -> {
            String key = resource == null ? "" : resource.toLowerCase(Locale.ROOT).trim();
            String normalizedAction = action == null ? "read" : action.toLowerCase(Locale.ROOT).trim();
            if ("features:attachment".equals(key) && "write".equals(normalizedAction)) {
                return List.of("ATTACHMENT_WRITE");
            }
            if ("features:attachment".equals(key) && "read".equals(normalizedAction)) {
                return List.of("ATTACHMENT_READ");
            }
            return List.of();
        };
    }

    private static EndpointModeGuard allowAllModes() {
        return (domain, action) -> true;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {
    }

    private record TestRagObjectAuthorizer(String writableObjectId) implements RagObjectAuthorizer {

        @Override
        public boolean supports(String objectType) {
            return "attachment".equals(objectType);
        }

        @Override
        public boolean canRead(String objectType, String objectId) {
            return false;
        }

        @Override
        public boolean canWrite(String objectType, String objectId) {
            return "attachment".equals(objectType) && writableObjectId.equals(objectId);
        }
    }
}
