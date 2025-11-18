package studio.one.platform.security.autoconfigure;

import java.util.concurrent.Executor;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.audit.LoginFailureEventListener;
import studio.one.base.security.audit.LoginFailureLogRetentionJob;
import studio.one.base.security.audit.LoginSuccessEventListener;
import studio.one.base.security.audit.domain.entity.LoginFailureLog;
import studio.one.base.security.audit.persistence.LoginFailureLogRepository;
import studio.one.base.security.audit.persistence.jdbc.LoginFailureLogJdbcRepository;
import studio.one.base.security.audit.persistence.jpa.LoginFailureLogJpaRepository;
import studio.one.base.security.audit.service.LoginFailureQueryService;
import studio.one.base.security.audit.service.LoginFailureQueryServiceImpl;
import studio.one.base.security.authentication.lock.service.AccountLockService;
import studio.one.base.security.web.controller.LoginFailureLogController;
import studio.one.base.security.web.mapper.LoginFailureLogMapper;
import studio.one.base.security.web.mapper.LoginFailureLogMapperImpl;
import studio.one.base.user.web.mapper.TimeMapper;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.security.autoconfigure.condition.ConditionalOnLoginFailurePersistence;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

/**
 *
 * @author donghyuck, son
 * @since 2025-11-01
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-01  donghyuck, son: 최초 생성.
 *          </pre>
 */

@AutoConfiguration
@EnableConfigurationProperties({ AuditProperties.class, PersistenceProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE, name = "enabled", havingValue = "true")
@Slf4j
public class LoginFailureAuditAutoConfiguration {

        private static final String DEFAULT_JPA_ENTITY_PACKAGE = LoginFailureLog.class.getPackageName();

        private static final String DEFAULT_JPA_REPOSITORY_PACKAGE = "studio.one.base.security.audit.persistence.jpa";

        private static final String FEATURE_NAME = "Security - Login Failure Audit";

        @ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE
                        + ".web", name = "enabled", havingValue = "true")
        @Bean(LoginFailureQueryService.SERVICE_NAME)
        @ConditionalOnMissingBean
        LoginFailureQueryService loginFailureQueryService(
                        LoginFailureLogRepository repository,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(LoginFailureQueryService.class, true),
                                LogUtils.red(State.CREATED.toString())));
                boolean isJdbc = repository instanceof LoginFailureLogJdbcRepository;
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.INFO + I18nKeys.AutoConfig.Feature.Service.INIT,
                                FEATURE_NAME,
                                LogUtils.blue(AccountLockService.class, true),
                                "LoginFailureLogRepository",
                                LogUtils.green(isJdbc ? LoginFailureLogJdbcRepository.class
                                                : LoginFailureLogJpaRepository.class,
                                                true)));

                return new LoginFailureQueryServiceImpl(repository);
        }

        @Bean
        @ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE
                        + ".web", name = "enabled", havingValue = "true")
        @ConditionalOnClass({ LoginFailureLogMapper.class, TimeMapper.class })
        @ConditionalOnMissingBean(LoginFailureLogMapper.class)
        public LoginFailureLogMapper loginFailureLogMapper(TimeMapper timeMapper) {
                return new LoginFailureLogMapperImpl(timeMapper);
        }

        @ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE
                        + ".web", name = "enabled", havingValue = "true")
        @Bean
        public LoginFailureLogController loginFailureLogController(
                        LoginFailureQueryService loginFailureQueryService,
                        LoginFailureLogMapper mapper,
                        AuditProperties props,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                                LogUtils.blue(FEATURE_NAME),
                                LogUtils.blue(LoginFailureLogController.class, true),
                                props.getLoginFailure().getWeb().getBasePath(), "READ"));
                return new LoginFailureLogController(loginFailureQueryService, mapper);
        }

        @Bean(ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EVENT_PUBLISHER)
        @ConditionalOnMissingBean
        AuthenticationEventPublisher authenticationEventPublsher(
                        org.springframework.context.ApplicationEventPublisher delegate,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AuthenticationEventPublisher.class, true),
                                LogUtils.red(State.CREATED.toString())));

                return new DefaultAuthenticationEventPublisher(delegate);
        }

        @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EXECUTOR)
        @ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE, name = "async", havingValue = "true" , matchIfMissing = true )
        public Executor loginFailureAuditExecutor(ObjectProvider<I18n> i18nProvider) {
                ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
                t.setCorePoolSize(2);
                t.setMaxPoolSize(4);
                t.setThreadNamePrefix("login-failure-audit-");
                t.initialize();
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(ThreadPoolTaskExecutor.class, true),
                                LogUtils.red(State.CREATED.toString())));

                return t;
        }

        @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EVENT_LISTENER)
        @ConditionalOnMissingBean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EVENT_LISTENER)
        public LoginFailureEventListener loginFailureEventListener(
                        ObjectProvider<AccountLockService> accountLockService,
                        ObjectProvider<LoginFailureLogRepository> loginFailureLogRepository,
                        AuditProperties props,
                        ObjectProvider<I18n> i18nProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(LoginFailureEventListener.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new LoginFailureEventListener(accountLockService, loginFailureLogRepository);
        }

        @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_SUCCESS_EVENT_LISTENER)
        @ConditionalOnMissingBean(name = ServiceNames.SECURITY_AUDIT_LOGIN_SUCCESS_EVENT_LISTENER)
        public LoginSuccessEventListener loginSuccessEventListener(
                        ObjectProvider<AccountLockService> accountLockService,
                        ObjectProvider<LoginFailureLogRepository> loginFailureLogRepository,
                        AuditProperties props,
                        ObjectProvider<I18n> i18nProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(LoginSuccessEventListener.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new LoginSuccessEventListener(accountLockService);
        }

        @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_LOG_RETENTION_JOB)
        @ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE, name = "retention-days")
        public LoginFailureLogRetentionJob loginFailureLogRetentionJob(
                        LoginFailureLogRepository repo,
                        AuditProperties props,
                        ObjectProvider<I18n> i18nProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(LoginFailureLogRetentionJob.class, true),
                                LogUtils.red(State.CREATED.toString())));

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.INFO + I18nKeys.AutoConfig.Feature.Service.INIT,
                                FEATURE_NAME,
                                LogUtils.blue(AccountLockService.class, true),
                                "LoginFailureLogRetentionJob Retention Days",
                                LogUtils.green(props.getLoginFailure().getRetentionDays().toString())));

                return new LoginFailureLogRetentionJob(repo, props.getLoginFailure().getRetentionDays());
        }

        @Configuration
        @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
        @ConditionalOnLoginFailurePersistence(PersistenceProperties.Type.jpa)
        @SuppressWarnings("java:S1118")
        static class EntityScanConfig {
                @Bean
                static BeanDefinitionRegistryPostProcessor auditEntityScanRegistrar(Environment env, I18n i18n) {
                        String entityKey = PropertyKeys.Security.Audit.LOGIN_FAILURE + ".entity-packages";
                        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EntityScan.PREPARING, "Jwt",
                                        entityKey,
                                        DEFAULT_JPA_ENTITY_PACKAGE));
                        return EntityScanRegistrarSupport.entityScanRegistrar(entityKey + ".entity-packages",
                                        DEFAULT_JPA_ENTITY_PACKAGE);
                }
        }

        @Configuration(proxyBeanMethods = false)
        @AutoConfigureAfter(EntityScanConfig.class)
        @ConditionalOnBean(EntityManagerFactory.class)
        @ConditionalOnLoginFailurePersistence(PersistenceProperties.Type.jpa)
        @EnableJpaRepositories(basePackages = DEFAULT_JPA_REPOSITORY_PACKAGE)
        static class JpaWiring {
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnLoginFailurePersistence(PersistenceProperties.Type.jdbc)
        static class JdbcWiring {
                @Bean
                @ConditionalOnMissingBean(LoginFailureLogRepository.class)
                LoginFailureLogRepository loginFailureLogJdbcRepository(
                                @Qualifier(ServiceNames.NAMED_JDBC_TEMPLATE) NamedParameterJdbcTemplate template) {
                        return new LoginFailureLogJdbcRepository(template);
                }
        }
}
