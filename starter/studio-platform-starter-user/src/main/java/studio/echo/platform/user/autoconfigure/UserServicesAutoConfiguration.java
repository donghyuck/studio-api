package studio.echo.platform.user.autoconfigure;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.repository.ApplicationCompanyRepository;
import studio.echo.base.user.domain.repository.ApplicationGroupMembershipRepository;
import studio.echo.base.user.domain.repository.ApplicationGroupRepository;
import studio.echo.base.user.domain.repository.ApplicationGroupRoleRepository;
import studio.echo.base.user.domain.repository.ApplicationRoleRepository;
import studio.echo.base.user.domain.repository.ApplicationUserRepository;
import studio.echo.base.user.domain.repository.ApplicationUserRoleRepository;
import studio.echo.base.user.service.ApplicationCompanyService;
import studio.echo.base.user.service.ApplicationGroupService;
import studio.echo.base.user.service.ApplicationRoleService;
import studio.echo.base.user.service.ApplicationUserService;
import studio.echo.base.user.service.impl.ApplicationCompanyServiceImpl;
import studio.echo.base.user.service.impl.ApplicationGroupServiceImpl;
import studio.echo.base.user.service.impl.ApplicationRoleServiceImpl;
import studio.echo.base.user.service.impl.ApplicationUserServiceImpl;
import studio.echo.platform.autoconfigure.i18n.I18nKeys;
import studio.echo.platform.component.State;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;
import studio.echo.platform.service.DomainEvents;
import studio.echo.platform.service.I18n;
import studio.echo.platform.service.Repository;
import studio.echo.platform.util.LogUtils;

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
    public ApplicationUserService<?> applicationUserService(
            ApplicationUserRepository userRepo,
            ApplicationRoleRepository roleRepo,
            ApplicationGroupRepository groupRepo,
            ApplicationUserRoleRepository userRoleRepo,
            ApplicationGroupMembershipRepository membershipRepo,
            ApplicationGroupRoleRepository groupRoleRepo,
            PasswordEncoder passwordEncoder,
            @Qualifier(ServiceNames.REPOSITORY) Repository repository,
            Clock clock
            ) {

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME, 
            LogUtils.blue(ApplicationUserServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        return new ApplicationUserServiceImpl(userRepo, roleRepo, groupRepo, userRoleRepo, membershipRepo, groupRoleRepo, passwordEncoder,(DomainEvents)repository, clock);
    }

    @Bean(name = ApplicationGroupService.SERVICE_NAME)
    @ConditionalOnMissingBean(ApplicationGroupService.class)
    @ConditionalOnClass({ ApplicationGroupRepository.class })
    public ApplicationGroupService applicationGroupService(
            ApplicationGroupRepository groupRepo,
            ApplicationUserRepository userRepo,
            ApplicationRoleRepository userRoleRepo,
            ApplicationGroupMembershipRepository membershipRepo,
            ApplicationGroupRoleRepository groupRoleRepo) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,  
            LogUtils.blue(ApplicationGroupServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        return new ApplicationGroupServiceImpl(groupRepo, userRepo, userRoleRepo, membershipRepo, groupRoleRepo);
    }

    @Bean(name = ApplicationRoleService.SERVICE_NAME)
    @ConditionalOnMissingBean(ApplicationRoleRepository.class)
    @ConditionalOnClass({ ApplicationRoleRepository.class })
    public ApplicationRoleService applicationRoleService(ApplicationRoleRepository roleRepo) {
        
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME, 
            LogUtils.blue(ApplicationRoleServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        return new ApplicationRoleServiceImpl(roleRepo);
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
