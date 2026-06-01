package studio.one.platform.user.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.base.user.domain.port.ApplicationCompanyMemberRepository;
import studio.one.base.user.domain.port.ApplicationCompanyMemberKeyRepository;
import studio.one.base.user.domain.port.ApplicationCompanyJoinRequestRepository;
import studio.one.base.user.domain.port.ApplicationCompanyPermissionPolicyRepository;
import studio.one.base.user.domain.port.ApplicationCompanyRepository;
import studio.one.base.user.domain.port.ApplicationRoleRepository;
import studio.one.base.user.domain.port.ApplicationUserRepository;
import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.base.user.application.usecase.ApplicationCompanyJoinRequestService;
import studio.one.base.user.application.usecase.ApplicationCompanyService;
import studio.one.base.user.application.usecase.ApplicationGroupService;
import studio.one.base.user.application.usecase.ApplicationRoleService;
import studio.one.base.user.application.usecase.ApplicationUserService;
import studio.one.base.user.application.usecase.PasswordPolicyService;
import studio.one.base.user.application.usecase.UserMutator;
import studio.one.base.user.web.controller.CompanyJoinRequestMgmtApi;
import studio.one.base.user.web.controller.CompanyMgmtController;
import studio.one.base.user.web.controller.CompanyJoinRequestMgmtController;
import studio.one.base.user.web.controller.CompanySelfJoinRequestApi;
import studio.one.base.user.web.controller.CompanySelfJoinRequestController;
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
            .withBean(ApplicationUserRepository.class, () -> stub(ApplicationUserRepository.class))
            .withPropertyValues("studio.features.user.enabled=true");

    @Test
    void registersCompanyServiceChainByDefault() {
        contextRunner
                .withBean(ApplicationCompanyRepository.class,
                        () -> stub(ApplicationCompanyRepository.class))
                .withBean(ApplicationCompanyMemberRepository.class,
                        () -> stub(ApplicationCompanyMemberRepository.class))
                .withBean(ApplicationCompanyMemberKeyRepository.class,
                        () -> stub(ApplicationCompanyMemberKeyRepository.class))
                .withBean(ApplicationCompanyJoinRequestRepository.class,
                        () -> stub(ApplicationCompanyJoinRequestRepository.class))
                .withBean(ApplicationCompanyPermissionPolicyRepository.class,
                        () -> stub(ApplicationCompanyPermissionPolicyRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ApplicationCompanyService.class);
                    assertThat(context).hasSingleBean(ApplicationCompanyMemberService.class);
                    assertThat(context).hasSingleBean(ApplicationCompanyPermissionService.class);
                    assertThat(context).hasSingleBean(ApplicationCompanyJoinRequestService.class);
                });
    }

    @Test
    void autoConfigurationImportsIncludeUserServices() throws Exception {
        String imports = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(
                        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(imports)
                .contains("studio.one.platform.user.autoconfigure.UserEntityAutoConfiguration")
                .contains("studio.one.platform.user.autoconfigure.UserServicesAutoConfiguration")
                .contains("studio.one.platform.user.autoconfigure.UserEndpointsAutoConfiguration");
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
                .withBean(ApplicationCompanyMemberKeyRepository.class,
                        () -> stub(ApplicationCompanyMemberKeyRepository.class))
                .withBean(ApplicationCompanyJoinRequestRepository.class,
                        () -> stub(ApplicationCompanyJoinRequestRepository.class))
                .withBean(ApplicationCompanyPermissionPolicyRepository.class,
                        () -> stub(ApplicationCompanyPermissionPolicyRepository.class))
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
                .withBean(ApplicationCompanyMemberKeyRepository.class,
                        () -> stub(ApplicationCompanyMemberKeyRepository.class))
                .withBean(ApplicationCompanyJoinRequestRepository.class,
                        () -> stub(ApplicationCompanyJoinRequestRepository.class))
                .withBean(ApplicationCompanyPermissionPolicyRepository.class,
                        () -> stub(ApplicationCompanyPermissionPolicyRepository.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApplicationCompanyService.class);
                    assertThat(context).doesNotHaveBean(ApplicationCompanyMemberService.class);
                    assertThat(context).doesNotHaveBean(ApplicationCompanyPermissionService.class);
                    assertThat(context).doesNotHaveBean(ApplicationCompanyJoinRequestService.class);
                });
    }

    @Test
    void doesNotRegisterDefaultHelperBeansWhenDefaultUserImplementationDisabled() {
        contextRunner
                .withPropertyValues("studio.features.user.use-default=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(UserMutator.class);
                    assertThat(context).doesNotHaveBean(PasswordPolicyService.class);
                });
    }

    @Test
    void registersUserBootstrapInitializerOnlyWhenEnabled() {
        contextRunner
                .run(context -> assertThat(context).doesNotHaveBean(UserBootstrapInitializer.class));

        contextRunner
                .withPropertyValues("studio.bootstrap.user.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(UserBootstrapInitializer.class));
    }

    @Test
    void passwordPolicyValidatorBacksOffForCustomPasswordPolicyService() {
        PasswordPolicyService customPolicy = new PasswordPolicyService() {
            @Override
            public studio.one.base.user.application.result.PasswordPolicyResult getPolicy() {
                return studio.one.base.user.application.result.PasswordPolicyResult.builder().build();
            }

            @Override
            public void validate(String password) {
            }
        };

        contextRunner
                .withBean(PasswordPolicyService.class, () -> customPolicy)
                .run(context -> assertThat(context.getBeansOfType(PasswordPolicyService.class))
                        .containsOnlyKeys("passwordPolicyService"));
    }

    @Test
    void joinRequestServiceBacksOffWhenRequiredRepositoriesArePartial() {
        contextRunner
                .withBean(ApplicationCompanyRepository.class,
                        () -> stub(ApplicationCompanyRepository.class))
                .withBean(ApplicationCompanyMemberRepository.class,
                        () -> stub(ApplicationCompanyMemberRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApplicationCompanyService.class);
                    assertThat(context).hasSingleBean(ApplicationCompanyMemberService.class);
                    assertThat(context).doesNotHaveBean(ApplicationCompanyPermissionService.class);
                    assertThat(context).doesNotHaveBean(ApplicationCompanyJoinRequestService.class);
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
                .withBean(ApplicationCompanyJoinRequestService.class, () -> stub(ApplicationCompanyJoinRequestService.class))
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
                    assertThat(context).hasSingleBean(CompanyJoinRequestMgmtController.class);
                    assertThat(context).doesNotHaveBean(CompanySelfJoinRequestController.class);
                });
    }

    @Test
    void registersCompanyEndpointWithoutJoinRequestServiceForLegacyFeatureLevelAccess() {
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
                    assertThat(context).doesNotHaveBean(CompanyJoinRequestMgmtController.class);
                    assertThat(context).doesNotHaveBean(CompanySelfJoinRequestController.class);
                });
    }

    @Test
    void joinRequestMgmtEndpointRequiresIdentityService() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        UserEndpointsAutoConfiguration.class))
                .withBean(I18n.class, () -> (code, args, locale) -> code)
                .withBean(ApplicationCompanyService.class, () -> stub(ApplicationCompanyService.class))
                .withBean(ApplicationCompanyMemberService.class, () -> stub(ApplicationCompanyMemberService.class))
                .withBean(ApplicationCompanyPermissionService.class, () -> stub(ApplicationCompanyPermissionService.class))
                .withBean(ApplicationCompanyJoinRequestService.class, () -> stub(ApplicationCompanyJoinRequestService.class))
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
                    assertThat(context).doesNotHaveBean(CompanyJoinRequestMgmtController.class);
                });
    }

    @Test
    void registersSelfJoinRequestEndpointOnlyWhenIdentityServiceAndSelfEndpointEnabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        UserEndpointsAutoConfiguration.class))
                .withBean(I18n.class, () -> (code, args, locale) -> code)
                .withBean(ApplicationCompanyJoinRequestService.class, () -> stub(ApplicationCompanyJoinRequestService.class))
                .withBean(IdentityService.class, () -> stub(IdentityService.class))
                .withPropertyValues(
                        "studio.features.user.web.enabled=true",
                        "studio.features.user.web.endpoints.group.enabled=false",
                        "studio.features.user.web.endpoints.user.enabled=false",
                        "studio.features.user.web.endpoints.public.enabled=false",
                        "studio.features.user.web.endpoints.role.enabled=false",
                        "studio.features.user.web.endpoints.company.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CompanySelfJoinRequestController.class);
                });
    }

    @Test
    void joinRequestEndpointsBackOffWhenApiContractBeansExist() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        UserEndpointsAutoConfiguration.class))
                .withBean(I18n.class, () -> (code, args, locale) -> code)
                .withBean(ApplicationCompanyService.class, () -> stub(ApplicationCompanyService.class))
                .withBean(ApplicationCompanyMemberService.class, () -> stub(ApplicationCompanyMemberService.class))
                .withBean(ApplicationCompanyPermissionService.class, () -> stub(ApplicationCompanyPermissionService.class))
                .withBean(ApplicationCompanyJoinRequestService.class, () -> stub(ApplicationCompanyJoinRequestService.class))
                .withBean(IdentityService.class, () -> stub(IdentityService.class))
                .withBean(CompanyJoinRequestMgmtApi.class, () -> stub(CompanyJoinRequestMgmtApi.class))
                .withBean(CompanySelfJoinRequestApi.class, () -> stub(CompanySelfJoinRequestApi.class))
                .withPropertyValues(
                        "studio.features.user.web.enabled=true",
                        "studio.features.user.web.endpoints.group.enabled=false",
                        "studio.features.user.web.endpoints.user.enabled=false",
                        "studio.features.user.web.endpoints.public.enabled=false",
                        "studio.features.user.web.endpoints.role.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CompanyJoinRequestMgmtController.class);
                    assertThat(context).doesNotHaveBean(CompanySelfJoinRequestController.class);
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
