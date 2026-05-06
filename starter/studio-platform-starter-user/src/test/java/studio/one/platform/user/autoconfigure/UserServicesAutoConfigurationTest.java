package studio.one.platform.user.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.base.user.persistence.ApplicationCompanyMemberRepository;
import studio.one.base.user.persistence.ApplicationCompanyRepository;
import studio.one.base.user.persistence.ApplicationRoleRepository;
import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.base.user.service.ApplicationCompanyPermissionService;
import studio.one.base.user.service.ApplicationCompanyService;
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.service.ApplicationRoleService;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.web.controller.CompanyMgmtController;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.service.I18n;

class UserServicesAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    UserServicesAutoConfiguration.class))
            .withBean(ApplicationUserService.class, () -> stub(ApplicationUserService.class))
            .withBean(ApplicationGroupService.class, () -> stub(ApplicationGroupService.class))
            .withBean(ApplicationRoleService.class, () -> stub(ApplicationRoleService.class))
            .withBean(ApplicationRoleRepository.class, () -> stub(ApplicationRoleRepository.class))
            .withPropertyValues("studio.features.user.enabled=true");

    @Test
    void registersCompanyServiceChainByDefault() {
        contextRunner
                .withBean(ApplicationCompanyRepository.class,
                        () -> stub(ApplicationCompanyRepository.class))
                .withBean(ApplicationCompanyMemberRepository.class,
                        () -> stub(ApplicationCompanyMemberRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ApplicationCompanyService.class);
                    assertThat(context).hasSingleBean(ApplicationCompanyMemberService.class);
                    assertThat(context).hasSingleBean(ApplicationCompanyPermissionService.class);
                });
    }

    @Test
    void backsOffWhenCompanyServiceBeanAlreadyExists() {
        ApplicationCompanyService customService = stub(ApplicationCompanyService.class);
        contextRunner
                .withBean(ApplicationCompanyService.class, () -> customService)
                .withBean(ApplicationCompanyRepository.class,
                        () -> stub(ApplicationCompanyRepository.class))
                .withBean(ApplicationCompanyMemberRepository.class,
                        () -> stub(ApplicationCompanyMemberRepository.class))
                .run(context -> assertThat(context.getBean(ApplicationCompanyService.class)).isSameAs(customService));
    }

    @Test
    void doesNotRegisterCompanyServicesWhenDefaultUserImplementationDisabled() {
        contextRunner
                .withPropertyValues("studio.features.user.use-default=false")
                .withBean(ApplicationCompanyRepository.class,
                        () -> stub(ApplicationCompanyRepository.class))
                .withBean(ApplicationCompanyMemberRepository.class,
                        () -> stub(ApplicationCompanyMemberRepository.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApplicationCompanyService.class);
                    assertThat(context).doesNotHaveBean(ApplicationCompanyMemberService.class);
                    assertThat(context).doesNotHaveBean(ApplicationCompanyPermissionService.class);
                });
    }

    @Test
    void registersCompanyEndpointWithEnvironmentProvider() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        UserEndpointsAutoConfiguration.class))
                .withBean(I18n.class, () -> (code, args, locale) -> code)
                .withBean(ApplicationCompanyService.class, () -> stub(ApplicationCompanyService.class))
                .withBean(ApplicationCompanyMemberService.class, () -> stub(ApplicationCompanyMemberService.class))
                .withBean(ApplicationCompanyPermissionService.class, () -> stub(ApplicationCompanyPermissionService.class))
                .withBean(IdentityService.class, () -> stub(IdentityService.class))
                .withPropertyValues(
                        "studio.features.user.web.enabled=true",
                        "studio.features.user.web.endpoints.group.enabled=false",
                        "studio.features.user.web.endpoints.user.enabled=false",
                        "studio.features.user.web.endpoints.public.enabled=false",
                        "studio.features.user.web.endpoints.role.enabled=false",
                        "studio.features.user.web.self.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CompanyMgmtController.class);
                });
    }

    @Test
    void registersCompanyEndpointWithoutIdentityServiceForLegacyFeatureLevelAccess() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        UserEndpointsAutoConfiguration.class))
                .withBean(I18n.class, () -> (code, args, locale) -> code)
                .withBean(ApplicationCompanyService.class, () -> stub(ApplicationCompanyService.class))
                .withBean(ApplicationCompanyMemberService.class, () -> stub(ApplicationCompanyMemberService.class))
                .withBean(ApplicationCompanyPermissionService.class, () -> stub(ApplicationCompanyPermissionService.class))
                .withPropertyValues(
                        "studio.features.user.web.enabled=true",
                        "studio.features.user.web.endpoints.group.enabled=false",
                        "studio.features.user.web.endpoints.user.enabled=false",
                        "studio.features.user.web.endpoints.public.enabled=false",
                        "studio.features.user.web.endpoints.role.enabled=false",
                        "studio.features.user.web.self.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CompanyMgmtController.class);
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "Stub";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
