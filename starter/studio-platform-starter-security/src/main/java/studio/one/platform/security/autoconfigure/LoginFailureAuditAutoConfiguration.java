package studio.one.platform.security.autoconfigure;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.Filter;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.audit.AccountLockAuthenticationFailureListener;
import studio.one.base.security.audit.ClientRequestDetailsAuthenticationDetailsSource;
import studio.one.base.security.audit.LoginFailureAuditFailureMonitor;
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
@AutoConfigureAfter(JwtAutoConfiguration.class)
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

        @Bean
        @ConditionalOnMissingBean
        LoginFailureAuditFailureMonitor loginFailureAuditFailureMonitor() {
                return new LoginFailureAuditFailureMonitor();
        }

        @Bean
        @ConditionalOnMissingBean
        ClientRequestDetailsAuthenticationDetailsSource clientRequestDetailsAuthenticationDetailsSource(
                        AuditProperties props) {
                var loginFailure = props.getLoginFailure();
                return new ClientRequestDetailsAuthenticationDetailsSource(
                                loginFailure.getCaptureIpHeader(),
                                loginFailure.getTrustedProxyCidrs());
        }

        @Bean
        @ConditionalOnClass({ SecurityFilterChain.class, AbstractAuthenticationProcessingFilter.class })
        static BeanPostProcessor clientRequestDetailsSecurityFilterChainPostProcessor(
                        ClientRequestDetailsAuthenticationDetailsSource detailsSource) {
                return new BeanPostProcessor() {
                        @Override
                        public Object postProcessAfterInitialization(Object bean, String beanName) {
                                if (bean instanceof SecurityFilterChain chain) {
                                        applyDetailsSource(chain.getFilters(), detailsSource);
                                }
                                return bean;
                        }

                        private void applyDetailsSource(List<Filter> filters,
                                        ClientRequestDetailsAuthenticationDetailsSource detailsSource) {
                                for (Filter filter : filters) {
                                        if (filter instanceof UsernamePasswordAuthenticationFilter authFilter) {
                                                applyDetailsSource(authFilter, detailsSource);
                                        }
                                }
                        }

                        private void applyDetailsSource(
                                        AbstractAuthenticationProcessingFilter authFilter,
                                        ClientRequestDetailsAuthenticationDetailsSource detailsSource) {
                                AuthenticationDetailsSource<?, ?> current = getDetailsSource(authFilter);
                                if (current == null
                                                || current.getClass().equals(WebAuthenticationDetailsSource.class)) {
                                        authFilter.setAuthenticationDetailsSource(detailsSource);
                                }
                        }

                        private AuthenticationDetailsSource<?, ?> getDetailsSource(
                                        AbstractAuthenticationProcessingFilter authFilter) {
                                try {
                                        var field = AbstractAuthenticationProcessingFilter.class
                                                        .getDeclaredField("authenticationDetailsSource");
                                        field.setAccessible(true);
                                        return (AuthenticationDetailsSource<?, ?>) field.get(authFilter);
                                } catch (ReflectiveOperationException ex) {
                                        return null;
                                }
                        }
                };
        }

        @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EXECUTOR)
        @ConditionalOnMissingBean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EXECUTOR)
        public Executor loginFailureAuditExecutor(
                        AuditProperties props,
                        LoginFailureAuditFailureMonitor failureMonitor,
                        ObjectProvider<I18n> i18nProvider) {
                if (!props.getLoginFailure().isAsync()) {
                        return new SyncTaskExecutor();
                }
                ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
                t.setCorePoolSize(2);
                t.setMaxPoolSize(4);
                t.setQueueCapacity(256);
                t.setThreadNamePrefix("login-failure-audit-");
                t.setRejectedExecutionHandler((task, executor) -> {
                        failureMonitor.recordRejectedExecution();
                        if (failureMonitor.shouldLogRejectedExecutionSummary()) {
                                log.warn("Login failure audit executor saturated. rejections={}",
                                                failureMonitor.getRejectedExecutionCount());
                        }
                        if (executor.isShutdown()) {
                                failureMonitor.recordDroppedExecution();
                                if (failureMonitor.shouldLogDroppedExecutionSummary()) {
                                        log.warn("Login failure audit task dropped because executor is shut down. drops={}",
                                                        failureMonitor.getDroppedExecutionCount());
                                }
                                return;
                        }
                        try {
                                if (!executor.getQueue().offer(task, 100, TimeUnit.MILLISECONDS)) {
                                        recordDroppedAuditTask(failureMonitor);
                                }
                        } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                recordDroppedAuditTask(failureMonitor);
                        }
                });
                t.initialize();
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(ThreadPoolTaskExecutor.class, true),
                                LogUtils.red(State.CREATED.toString())));

                return t;
        }

        private static void recordDroppedAuditTask(LoginFailureAuditFailureMonitor failureMonitor) {
                failureMonitor.recordDroppedExecution();
                if (failureMonitor.shouldLogDroppedExecutionSummary()) {
                        log.warn("Login failure audit task dropped after bounded wait. drops={}",
                                        failureMonitor.getDroppedExecutionCount());
                }
        }

        @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EVENT_LISTENER)
        @ConditionalOnMissingBean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EVENT_LISTENER)
        public LoginFailureEventListener loginFailureEventListener(
                        LoginFailureLogRepository loginFailureLogRepository,
                        LoginFailureAuditFailureMonitor failureMonitor,
                        @Qualifier(ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EXECUTOR) Executor auditExecutor,
                        ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
                        ObjectProvider<I18n> i18nProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(LoginFailureEventListener.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new LoginFailureEventListener(
                                loginFailureLogRepository,
                                failureMonitor,
                                auditExecutor,
                                loginFailureAuditTransaction(transactionManagerProvider));
        }

        private static TransactionOperations loginFailureAuditTransaction(
                        ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
                PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
                if (transactionManager == null) {
                        return null;
                }
                TransactionTemplate template = new TransactionTemplate(transactionManager);
                template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                return template;
        }

        @Bean
        @ConditionalOnMissingBean
        public AccountLockAuthenticationFailureListener accountLockAuthenticationFailureListener(
                        ObjectProvider<AccountLockService> accountLockService) {
                return new AccountLockAuthenticationFailureListener(accountLockService);
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
                        return EntityScanRegistrarSupport.entityScanRegistrar(entityKey,
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
                        SecurityJdbcDatabaseSupport.requirePostgreSQL(template, "login failure audit");
                        return new LoginFailureLogJdbcRepository(template);
                }
        }
}
