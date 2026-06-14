package studio.one.platform.documentconvert.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.documentconvert.application.service.DocumentConvertService;

class DocumentConvertAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocumentConvertAutoConfiguration.class));

    @Test
    void isDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DocumentConvertService.class));
    }
}
