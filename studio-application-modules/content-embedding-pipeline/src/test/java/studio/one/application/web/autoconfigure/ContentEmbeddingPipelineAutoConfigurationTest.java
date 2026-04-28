package studio.one.application.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.service.AttachmentRagIndexJobSourceExecutor;
import studio.one.application.web.service.AttachmentRagIndexJobSourceNameResolver;
import studio.one.application.web.service.AttachmentRagIndexService;
import studio.one.application.web.service.AttachmentStructuredRagIndexer;
import studio.one.application.web.service.DefaultAttachmentStructuredRagIndexer;

class ContentEmbeddingPipelineAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ContentEmbeddingPipelineAutoConfiguration.class));

    @Test
    void registersAttachmentRagIndexServicesWhenAttachmentServiceExists() {
        contextRunner
                .withBean(AttachmentService.class, () -> mock(AttachmentService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentRagIndexService.class);
                    assertThat(context).hasSingleBean(AttachmentRagIndexJobSourceExecutor.class);
                    assertThat(context).hasSingleBean(AttachmentRagIndexJobSourceNameResolver.class);
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
        });
    }
}
