package studio.one.platform.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import studio.one.aplication.security.auth.password.MailService;
import studio.one.aplication.security.auth.password.PasswordResetService;
import studio.one.aplication.security.auth.password.impl.MailServiceImpl;
import studio.one.aplication.security.web.controller.PasswordResetController;
import studio.one.base.security.jwt.reset.persistence.PasswordResetTokenRepository;
import studio.one.base.user.service.ApplicationUserService;

class AccountPasswordResetAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    AccountPasswordResetAutoConfiguration.class))
            .withPropertyValues(
                    "studio.security.auth.password-reset.enabled=true",
                    "studio.security.auth.password-reset.persistence=jdbc",
                    "studio.persistence.type=jdbc")
            .withBean(PasswordResetTokenRepository.class, () -> mock(PasswordResetTokenRepository.class))
            .withBean(ApplicationUserService.class, () -> mock(ApplicationUserService.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));

    @Test
    void doesNotCreateMailServiceOrPasswordResetServiceWhenJavaMailSenderBeanIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MailService.class);
            assertThat(context).doesNotHaveBean(PasswordResetService.class);
            assertThat(context).doesNotHaveBean(PasswordResetController.class);
        });
    }

    @Test
    void createsDefaultMailServiceWhenJavaMailSenderBeanExists() {
        contextRunner
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MailService.class);
                    assertThat(context.getBean(MailService.class)).isInstanceOf(MailServiceImpl.class);
                    assertThat(context).hasSingleBean(PasswordResetService.class);
                    assertThat(context).hasSingleBean(PasswordResetController.class);
                });
    }

    @Test
    void usesCustomMailServiceWithoutJavaMailSenderBean() {
        MailService customMailService = (to, token) -> {
        };

        contextRunner
                .withBean(MailService.class, () -> customMailService)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MailService.class);
                    assertThat(context.getBean(MailService.class)).isSameAs(customMailService);
                    assertThat(context).hasSingleBean(PasswordResetService.class);
                    assertThat(context).hasSingleBean(PasswordResetController.class);
                });
    }

    @Test
    void preservesCustomMailServiceWhenJavaMailSenderBeanExists() {
        MailService customMailService = (to, token) -> {
        };

        contextRunner
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withBean(MailService.class, () -> customMailService)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MailService.class);
                    assertThat(context.getBean(MailService.class)).isSameAs(customMailService);
                    assertThat(context).hasSingleBean(PasswordResetService.class);
                });
    }
}
