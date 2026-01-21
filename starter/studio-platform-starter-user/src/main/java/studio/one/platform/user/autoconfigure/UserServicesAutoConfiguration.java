package studio.one.platform.user.autoconfigure;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.event.listener.UserCacheEvictListener;
import studio.one.base.user.persistence.ApplicationCompanyRepository;
import studio.one.base.user.persistence.ApplicationGroupMembershipRepository;
import studio.one.base.user.persistence.ApplicationGroupRepository;
import studio.one.base.user.persistence.ApplicationGroupRoleRepository;
import studio.one.base.user.persistence.ApplicationRoleRepository;
import studio.one.base.user.persistence.ApplicationUserRoleRepository;
import studio.one.base.user.persistence.ApplicationUserRepository;
import studio.one.base.user.service.ApplicationCompanyService;
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.service.ApplicationRoleService;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.service.UserMutator;
import studio.one.base.user.service.impl.ApplicationCompanyServiceImpl;
import studio.one.base.user.service.impl.ApplicationGroupServiceImpl;
import studio.one.base.user.service.impl.ApplicationRoleServiceImpl;
import studio.one.base.user.service.impl.ApplicationUserMutator;
import studio.one.base.user.service.impl.ApplicationUserServiceImpl;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.service.DomainEvents;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties({ PersistenceProperties.class, UserFeatureProperties.class })
@AutoConfigureAfter(UserEntityAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "enabled", havingValue = "true")
@Slf4j
public class UserServicesAutoConfiguration {

        protected static final String FEATURE_NAME = "User";
        private final ObjectProvider<I18n> i18nProvider;

        @Bean
        @ConditionalOnMissingBean
        public Clock jwtClock() {
                return Clock.systemUTC();
        }

        @Bean
        @ConditionalOnMissingBean(name = IdentityService.SERVICE_NAME)
        @ConditionalOnExpression("${" + PropertyKeys.Features.User.ENABLED + ":true} && ${" + PropertyKeys.Features.User.USE_DEFAULT + ":true}")
        public ApplicationRunner identityServiceWarning(ObjectProvider<IdentityService> identityServiceProvider) {
                return args -> {
                        if (identityServiceProvider.getIfAvailable() == null) {
                                log.warn("IdentityService bean is missing; provide a custom implementation or include studio-platform-user-default.");
                        }
                };
        }

        @Bean
        @ConditionalOnMissingBean(UserMutator.class)
        public UserMutator<?> userMutator() {
                return new ApplicationUserMutator();
        }

        @Bean(name = ApplicationUserService.SERVICE_NAME)
        @ConditionalOnClass(ApplicationUserRepository.class)
        @ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "use-default", havingValue = "true", matchIfMissing = true)
        @ConditionalOnMissingBean(ApplicationUserService.class)
        public ApplicationUserService applicationUserService(
                        @Qualifier(ServiceNames.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate,
                        ApplicationUserRepository userRepo,
                        ApplicationRoleRepository roleRepo,
                        ApplicationGroupRepository groupRepo,
                        ApplicationUserRoleRepository userRoleRepo,
                        ApplicationGroupMembershipRepository membershipRepo,
                        ApplicationGroupRoleRepository groupRoleRepo,
                        ObjectProvider<PasswordEncoder> passwordEncoder,
                        @Qualifier(ServiceNames.REPOSITORY) ObjectProvider<DomainEvents> domainEventsProvider,
                        Clock clock,
                        ObjectProvider<UserMutator<?>> userMutatorProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(ApplicationUserServiceImpl.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new ApplicationUserServiceImpl(userRepo, roleRepo, groupRepo, userRoleRepo, membershipRepo,
                                jdbcTemplate, passwordEncoder, domainEventsProvider, clock, i18nProvider,
                                (UserMutator) userMutatorProvider.getIfAvailable(ApplicationUserMutator::new));
        }

        @Bean(name = ApplicationGroupService.SERVICE_NAME)
        @ConditionalOnMissingBean(ApplicationGroupService.class)
        @ConditionalOnClass({ ApplicationGroupRepository.class })
        @ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "use-default", havingValue = "true", matchIfMissing = true)
        public ApplicationGroupService applicationGroupService(
                        @Qualifier(ServiceNames.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate,
                        ApplicationGroupRepository groupRepo,
                        ApplicationRoleRepository userRoleRepo,
                        ApplicationGroupMembershipRepository membershipRepo,
                        ApplicationGroupRoleRepository groupRoleRepo) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(ApplicationGroupServiceImpl.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new ApplicationGroupServiceImpl(groupRepo, userRoleRepo, membershipRepo, groupRoleRepo,
                                jdbcTemplate, i18nProvider);
        }

        @Bean(name = ApplicationRoleService.SERVICE_NAME)
        @ConditionalOnMissingBean(ApplicationRoleRepository.class)
        @ConditionalOnClass({ ApplicationRoleRepository.class })
        @ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "use-default", havingValue = "true", matchIfMissing = true)
        public ApplicationRoleService applicationRoleService(
                        @Qualifier(ServiceNames.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate,
                        ApplicationRoleRepository roleRepo,
                        ApplicationGroupRoleRepository groupRoleRepo,
                        ApplicationUserRoleRepository userRoleRepo,
                        @Qualifier(ServiceNames.REPOSITORY) ObjectProvider<DomainEvents> domainEventsProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(ApplicationRoleServiceImpl.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new ApplicationRoleServiceImpl(roleRepo, groupRoleRepo, userRoleRepo, jdbcTemplate,
                                domainEventsProvider, i18nProvider);
        }

        @Bean(name = ApplicationCompanyService.SERVICE_NAME)
        @ConditionalOnMissingBean(ApplicationCompanyService.class)
        @ConditionalOnClass({ ApplicationCompanyRepository.class })
        @ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "use-default", havingValue = "true", matchIfMissing = true)
        public ApplicationCompanyService applicationCompanyService(ApplicationCompanyRepository companyRepo) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(ApplicationCompanyServiceImpl.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new ApplicationCompanyServiceImpl(companyRepo, i18nProvider);
        }

        @Bean
        @ConditionalOnMissingBean(UserCacheEvictListener.class)
        @ConditionalOnBean(CacheManager.class)
        UserCacheEvictListener userCacheEvictListener(ObjectProvider<CacheManager> cacheManager) {
                I18n i18n = I18nUtils.resolve(i18nProvider); 
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(UserCacheEvictListener.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new UserCacheEvictListener(cacheManager.getIfAvailable());
        }
}
