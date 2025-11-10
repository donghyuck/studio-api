package studio.one.platform.user.autoconfigure;

import java.time.Clock;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.repository.ApplicationCompanyRepository;
import studio.one.base.user.domain.repository.ApplicationGroupMembershipRepository;
import studio.one.base.user.domain.repository.ApplicationGroupRepository;
import studio.one.base.user.domain.repository.ApplicationGroupRoleRepository;
import studio.one.base.user.domain.repository.ApplicationRoleRepository;
import studio.one.base.user.domain.repository.ApplicationUserRepository;
import studio.one.base.user.domain.repository.ApplicationUserRoleRepository;
import studio.one.base.user.service.ApplicationCompanyService;
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.service.ApplicationRoleService;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.service.impl.ApplicationCompanyServiceImpl;
import studio.one.base.user.service.impl.ApplicationGroupServiceImpl;
import studio.one.base.user.service.impl.ApplicationRoleServiceImpl;
import studio.one.base.user.service.impl.ApplicationUserServiceImpl;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.DomainEvents;
import studio.one.platform.service.I18n;
import studio.one.platform.service.Repository;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties({ UserFeatureProperties.class })
@AutoConfigureAfter(UserEntityAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "enabled", havingValue = "true")
@Slf4j
public class UserServicesAutoConfiguration {

    private static final String FEATURE_NAME = "User";
    private final I18n i18n;
    
    @Bean
    @ConditionalOnMissingBean
    public Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean(name = ApplicationUserService.SERVICE_NAME)
    @ConditionalOnClass(ApplicationUserRepository.class)
    @ConditionalOnMissingBean(ApplicationUserService.class)
    public ApplicationUserService<?,?> applicationUserService(
           @Qualifier(ServiceNames.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate,
            ApplicationUserRepository userRepo,
            ApplicationRoleRepository roleRepo,
            ApplicationGroupRepository groupRepo,
            ApplicationUserRoleRepository userRoleRepo,
            ApplicationGroupMembershipRepository membershipRepo,
            ApplicationGroupRoleRepository groupRoleRepo,
            PasswordEncoder passwordEncoder,
            @Qualifier(ServiceNames.REPOSITORY) Repository repository,
            Clock clock) {

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(ApplicationUserServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        return new ApplicationUserServiceImpl(userRepo, roleRepo, groupRepo, userRoleRepo, membershipRepo,
                jdbcTemplate, passwordEncoder, (DomainEvents) repository, clock);
    }

    @Bean(name = ApplicationGroupService.SERVICE_NAME)
    @ConditionalOnMissingBean(ApplicationGroupService.class)
    @ConditionalOnClass({ ApplicationGroupRepository.class })
    @ConditionalOnBean(EntityManagerFactory.class)
    public ApplicationGroupService applicationGroupService(
            @Qualifier(ServiceNames.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate,
            ApplicationGroupRepository groupRepo,
            ApplicationUserRepository userRepo,
            ApplicationRoleRepository userRoleRepo,
            ApplicationGroupMembershipRepository membershipRepo,
            ApplicationGroupRoleRepository groupRoleRepo) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(ApplicationGroupServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        return new ApplicationGroupServiceImpl(groupRepo, userRepo, userRoleRepo, membershipRepo, groupRoleRepo,
                jdbcTemplate);
    }

    @Bean(name = ApplicationRoleService.SERVICE_NAME)
    @ConditionalOnMissingBean(ApplicationRoleRepository.class)
    @ConditionalOnClass({ ApplicationRoleRepository.class })
    public ApplicationRoleService applicationRoleService(
        @Qualifier(ServiceNames.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate,
        ApplicationRoleRepository roleRepo,
        ApplicationGroupRoleRepository groupRoleRepo,
        ApplicationUserRoleRepository userRoleRepo
        ) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(ApplicationRoleServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        return new ApplicationRoleServiceImpl(roleRepo, groupRoleRepo, userRoleRepo, jdbcTemplate);
    }

    @Bean(name = ApplicationCompanyService.SERVICE_NAME)
    @ConditionalOnMissingBean(ApplicationCompanyService.class)
    @ConditionalOnClass({ ApplicationCompanyRepository.class })
    public ApplicationCompanyService applicationCompanyService(ApplicationCompanyRepository companyRepo) {

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(ApplicationCompanyServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        return new ApplicationCompanyServiceImpl(companyRepo);
    }
}
