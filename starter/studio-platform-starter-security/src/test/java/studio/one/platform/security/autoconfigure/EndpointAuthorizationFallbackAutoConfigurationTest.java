package studio.one.platform.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.constant.ServiceNames;
import studio.one.platform.security.authz.DenyAllEndpointAuthorization;
import studio.one.platform.service.I18n;

class EndpointAuthorizationFallbackAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EndpointAuthorizationFallbackAutoConfiguration.class))
            .withPropertyValues("studio.security.enabled=true")
            .withBean(I18n.class, () -> (code, args, locale) -> code);

    @Test
    void registersFailClosedFallbackWhenNoAuthorizationBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DenyAllEndpointAuthorization.class);
            assertThat(context).hasBean(ServiceNames.DOMAIN_ENDPOINT_AUTHZ);
        });
    }

    @Test
    void backsOffWhenAuthorizationBeanAlreadyExists() {
        Object endpointAuthorization = new Object();

        contextRunner
                .withBean(ServiceNames.DOMAIN_ENDPOINT_AUTHZ, Object.class, () -> endpointAuthorization)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DenyAllEndpointAuthorization.class);
                    assertThat(context.getBean(ServiceNames.DOMAIN_ENDPOINT_AUTHZ))
                            .isSameAs(endpointAuthorization);
                });
    }
}
