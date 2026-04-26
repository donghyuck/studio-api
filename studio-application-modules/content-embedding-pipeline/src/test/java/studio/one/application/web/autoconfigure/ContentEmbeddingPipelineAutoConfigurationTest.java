package studio.one.application.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.service.AttachmentRagIndexJobSourceExecutor;
import studio.one.application.web.service.AttachmentRagIndexService;

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
                });
    }

    @Test
    void backsOffWhenAttachmentServiceIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AttachmentRagIndexService.class);
            assertThat(context).doesNotHaveBean(AttachmentRagIndexJobSourceExecutor.class);
        });
    }
}
