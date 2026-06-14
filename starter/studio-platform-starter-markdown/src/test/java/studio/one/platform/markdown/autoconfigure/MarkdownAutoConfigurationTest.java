package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.platform.documentconvert.application.service.DocumentConvertService;
import studio.one.platform.markdown.application.MarkdownDocumentService;
import studio.one.platform.markdown.application.port.MarkdownConversionPort;
import studio.one.platform.markdown.application.port.MarkdownNativeExtractorPort;
import studio.one.platform.markdown.application.port.MarkdownPipelinePort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;
import studio.one.platform.markdown.application.port.MarkdownTaskExecutor;
import studio.one.platform.markdown.web.MarkdownDocumentController;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;

class MarkdownAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MarkdownAutoConfiguration.class,
                    MarkdownWebAutoConfiguration.class))
            .withUserConfiguration(RequiredBeans.class);

    @Test
    void remainsDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(MarkdownDocumentService.class);
            assertThat(context).doesNotHaveBean(MarkdownDocumentController.class);
        });
    }

    @Test
    void registersServiceAndControllerWhenEnabled() {
        runner.withPropertyValues("studio.markdown.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MarkdownDocumentService.class);
                    assertThat(context).hasSingleBean(MarkdownDocumentController.class);
                    assertThat(context).hasSingleBean(MarkdownTaskExecutor.class);
                    assertThat(context).hasBean("markdownTaskExecutor");
                });
    }

    @Test
    void registersCompleteStackFromExternalServices() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MarkdownAutoConfiguration.class,
                        MarkdownWebAutoConfiguration.class))
                .withUserConfiguration(ExternalRequiredBeans.class)
                .withPropertyValues("studio.markdown.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MarkdownRepository.class);
                    assertThat(context).hasSingleBean(MarkdownSourcePort.class);
                    assertThat(context).hasSingleBean(MarkdownNativeExtractorPort.class);
                    assertThat(context).hasSingleBean(MarkdownConversionPort.class);
                    assertThat(context).hasSingleBean(MarkdownDocumentService.class);
                    assertThat(context).hasSingleBean(MarkdownDocumentController.class);
                });
    }

    @Test
    void doesNotRegisterControllerWithoutService() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MarkdownWebAutoConfiguration.class))
                .withPropertyValues("studio.markdown.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(MarkdownDocumentController.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredBeans {
        @Bean
        MarkdownRepository markdownRepository() {
            return mock(MarkdownRepository.class);
        }

        @Bean
        MarkdownSourcePort markdownSourcePort() {
            return mock(MarkdownSourcePort.class);
        }

        @Bean
        MarkdownNativeExtractorPort markdownNativeExtractorPort() {
            return mock(MarkdownNativeExtractorPort.class);
        }

        @Bean
        MarkdownConversionPort markdownConversionPort() {
            return mock(MarkdownConversionPort.class);
        }

        @Bean
        MarkdownPipelinePort markdownPipelinePort() {
            return MarkdownPipelinePort.noop();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExternalRequiredBeans {
        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
            return mock(NamedParameterJdbcTemplate.class);
        }

        @Bean
        AttachmentService attachmentService() {
            return mock(AttachmentService.class);
        }

        @Bean
        FileContentExtractionService fileContentExtractionService() {
            return mock(FileContentExtractionService.class);
        }

        @Bean
        DocumentConvertService documentConvertService() {
            return mock(DocumentConvertService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
